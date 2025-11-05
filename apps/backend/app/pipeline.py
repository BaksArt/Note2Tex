import time, uuid, csv
from pathlib import Path
import httpx

from app.utils.detect_blocks import detect_blocks
from app.utils.recognize_formula import recognize_crops
from app.utils.recognize_word import recognize_word, load_htr_model
from app.utils.assemble_latex import write_mixed_latex_file
from app.utils.latex_to_pdf import compile_tex_file_to_pdf

async def download_image(url: str, dest_dir: str) -> str:
    inbox = Path(dest_dir) / "inbox"; inbox.mkdir(parents=True, exist_ok=True)
    fn = f"page_{uuid.uuid4().hex}.png"; dst = inbox / fn
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.get(url); r.raise_for_status(); dst.write_bytes(r.content)
    return str(dst)

def run_full_pipeline(
    image_path: str,
    detector_weights: str,
    trocr_dir: str,
    words_ocr_weights: str,
    det_conf: float, det_iou: float, det_imgsz: int, det_pad: float,
    beams: int, max_new_tokens: int, length_penalty: float,
    bin_strength: float,
    erode_kernel: int,
    temp_dir: str,
    make_tex: bool = True,
    make_csv: bool = True,
    make_pdf: bool = True,
    htr_weights: str = "models/words_recognizer/ocr_transformer.pt",
):
    t0 = time.time()
    work_dir = Path(temp_dir) / "work"
    work_dir.parent.mkdir(parents=True, exist_ok=True)

    det_results = detect_blocks(
        image_path=image_path,
        yolo_weights=detector_weights,
        conf=det_conf, iou=det_iou, imgsz=det_imgsz, pad=det_pad,
        temp_dir=str(work_dir), save_viz=True,
    )
    if not det_results:
        return {
            "latex": "", "blocks": [], "tex_path": None, "csv_path": None, "pdf_path": None,
            "time_ms": int((time.time() - t0) * 1000), "model_version": "trocr-custom",
            "detector_weights": detector_weights,
        }
    print("det_results:", det_results)

    formula_items = [d for d in det_results if d.get("cls") == "formula"]
    formula_crop_paths = [d["crop_path"] for d in formula_items]

    rec_formulas = recognize_crops(
        crop_paths=formula_crop_paths, model_dir=trocr_dir,
        beams=beams, max_new_tokens=max_new_tokens, length_penalty=length_penalty,
        bin_strength=bin_strength, erode_kernel=erode_kernel, out_dir=str(work_dir),
    )
    latex_by_idx = {}
    for idx, latex, bin_path in rec_formulas:
        latex_by_idx[idx] = (latex, bin_path)

    htr_model, _ = load_htr_model(htr_weights)
    text_by_idx = {}
    for d in det_results:
        if d.get("cls") != "text_line":
            continue
        try:
            text, conf = recognize_word(d["crop_path"], weights_path=words_ocr_weights, model=htr_model)
        except Exception:
            text, conf = "", 0.0
        text_by_idx[d["idx"]] = (text, conf)

    # финальный список блоков для сборки
    blocks = []
    for d in det_results:
        idx = d["idx"]; bbox = d["bbox"]; crop = d["crop_path"]
        if d.get("cls") == "formula":
            latex, bin_path = latex_by_idx.get(idx, ("", ""))
            blocks.append({
                "idx": idx, "bbox": bbox, "kind": "formula",
                "content": latex, "crop_path": crop, "alt_path": bin_path,
            })
        else:
            txt, conf = text_by_idx.get(idx, ("", 0.0))
            blocks.append({
                "idx": idx, "bbox": bbox, "kind": "text",
                "content": txt, "crop_path": crop, "alt_path": None, "conf": conf,
            })

    tex_path = pdf_path = csv_path = None

    if make_tex:
        items_for_doc = [
            (b["idx"], b["kind"], b["content"], b["bbox"][0], b["bbox"][1], b["bbox"][2], b["bbox"][3])
            for b in blocks
        ]
        tex_path = write_mixed_latex_file(
            items=items_for_doc,
            out_path=str(work_dir / "page.tex"),
            title=f""
        )
        if make_pdf:
            try:
                pdf_obj = compile_tex_file_to_pdf(tex_path, engine="pdflatex", timeout=240)
                pdf_path = str(pdf_obj.resolve())
            except Exception as e:
                print(f"[latex] PDF compile failed: {e}")
                pdf_path = None

    if make_csv:
        csv_path = str(work_dir / "results.csv")
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["idx", "kind", "content", "alt_path", "crop_path", "x1", "y1", "x2", "y2"])
            for b in sorted(blocks, key=lambda x: x["idx"]):
                x1, y1, x2, y2 = b["bbox"]
                w.writerow([b["idx"], b["kind"], b["content"], b["alt_path"], b["crop_path"], x1, y1, x2, y2])

    return {
        "latex": "\n\n".join([
            (f"\\[\n{b['content']}\n\\]\n" if b["kind"] == "formula" else b["content"])
            for b in sorted(blocks, key=lambda x: x["idx"])
        ]),
        "blocks": blocks,
        "tex_path": tex_path, "pdf_path": pdf_path, "csv_path": csv_path,
        "time_ms": int((time.time() - t0) * 1000),
        "model_version": "trocr-custom",
        "detector_weights": detector_weights,
    }
