import time, uuid
from pathlib import Path
import httpx
import csv

from detect_blocks import detect_formulas
from recognize_formula import recognize_crops
from assemble_latex import write_latex_file

from latex_pdf import compile_tex_file_to_pdf


async def download_image(url: str, dest_dir: str) -> str:
    inbox = Path(dest_dir) / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    fn = f"page_{uuid.uuid4().hex}.png"
    dst = inbox / fn
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.get(url)
        r.raise_for_status()
        dst.write_bytes(r.content)
    return str(dst)


def run_full_pipeline(
    image_path: str,
    detector_weights: str,
    trocr_dir: str,
    det_conf: float, det_iou: float, det_imgsz: int, det_pad: float,
    beams: int, max_new_tokens: int, length_penalty: float,
    bin_strength: float,
    temp_dir: str,
    make_tex: bool = True,
    make_csv: bool = True,
    make_pdf: bool = True,
):
    t0 = time.time()

    work_dir = Path(temp_dir) / "work"
    work_dir.parent.mkdir(parents=True, exist_ok=True)

    det_results = detect_formulas(
        image_path=image_path,
        yolo_weights=detector_weights,
        conf=det_conf, iou=det_iou, imgsz=det_imgsz, pad=det_pad,
        temp_dir=str(work_dir),
        save_viz=True,
    )
    if not det_results:
        return {
            "latex": "",
            "blocks": [],
            "tex_path": None,
            "csv_path": None,
            "pdf_path": None,
            "time_ms": int((time.time() - t0) * 1000),
            "model_version": "trocr-custom",
            "detector_weights": detector_weights,
        }

    crop_paths = [d["crop_path"] for d in det_results]
    rec = recognize_crops(
        crop_paths=crop_paths,
        model_dir=trocr_dir,
        beams=beams,
        max_new_tokens=max_new_tokens,
        length_penalty=length_penalty,
        bin_strength=bin_strength,
        out_dir=str(work_dir),
    )

    blocks = []
    bbox_by_idx = {d["idx"]: d["bbox"] for d in det_results}
    for idx, latex, bin_path in rec:
        raw_path = str(work_dir / f"raw_block_{idx:03d}.png")
        blocks.append({
            "idx": idx,
            "bbox": bbox_by_idx.get(idx, (0, 0, 0, 0)),
            "latex": latex,
            "crop_path": raw_path,
            "bin_path": bin_path,
        })

    tex_path, pdf_path, csv_path = None, None, None

    if make_tex:
        tex_path = write_latex_file(
            [(b["idx"], b["latex"]) for b in blocks],
            out_path=str(work_dir / "formulas.tex"),
            title=f"Recognized Formulas — {Path(image_path).name}"
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
            w.writerow(["idx", "latex", "bin_path", "crop_path", "x1", "y1", "x2", "y2"])
            for b in sorted(blocks, key=lambda x: x["idx"]):
                x1, y1, x2, y2 = b["bbox"]
                w.writerow([b["idx"], b["latex"], b["bin_path"], b["crop_path"], x1, y1, x2, y2])


    return {
        "latex": "\n\n".join([b["latex"] for b in sorted(blocks, key=lambda x: x["idx"])]),
        "blocks": blocks,
        "tex_path": tex_path,
        "pdf_path": pdf_path,
        "csv_path": csv_path,
        "time_ms": int((time.time() - t0) * 1000),
        "model_version": "trocr-custom",
        "detector_weights": detector_weights,
    }
