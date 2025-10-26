import argparse
from pathlib import Path
import csv

from detect_blocks import detect_formulas
from recognize_formula import recognize_crops
from assemble_latex import write_latex_file

def parse_args():
    ap = argparse.ArgumentParser(description="Full pipeline: detect formulas -> binarize -> recognize -> LaTeX")
    ap.add_argument("--image", type=str, required=True, help="Большая страница (png/jpg)")
    ap.add_argument("--detector", type=str, default="models/detector/best.pt")
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--iou", type=float, default=0.5)
    ap.add_argument("--imgsz", type=int, default=1280)
    ap.add_argument("--pad", type=float, default=0.04)
    ap.add_argument("--save_viz", action="store_true")
    ap.add_argument("--trocr_dir", type=str, default="models/trocr_latex_fast")
    ap.add_argument("--beams", type=int, default=4)
    ap.add_argument("--max_new_tokens", type=int, default=224)
    ap.add_argument("--length_penalty", type=float, default=1.1)
    ap.add_argument("--bin_strength", type=float, default=0.9)
    ap.add_argument("--temp_dir", type=str, default="temp")
    ap.add_argument("--tex_out", type=str, default="temp/formulas.tex", help="Куда сохранить собранный .tex")
    ap.add_argument("--csv_out", type=str, default="temp/results.csv", help="Куда сохранить CSV с результатами")
    return ap.parse_args()

def main():
    args = parse_args()
    img_name = Path(args.image).name

    det = detect_formulas(
        image_path=args.image,
        yolo_weights=args.detector,
        conf=args.conf,
        iou=args.iou,
        imgsz=args.imgsz,
        pad=args.pad,
        temp_dir=args.temp_dir,
        save_viz=args.save_viz,
    )
    if not det:
        print("[pipeline] Нет формул, сборка .tex пропущена.")
        return

    crop_paths = [d["crop_path"] for d in det]

    rec = recognize_crops(
        crop_paths=crop_paths,
        model_dir=args.trocr_dir,
        beams=args.beams,
        max_new_tokens=args.max_new_tokens,
        length_penalty=args.length_penalty,
        bin_strength=args.bin_strength,
        out_dir=args.temp_dir,
    )
    csv_path = Path(args.csv_out)
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["idx", "latex", "bin_path", "x1", "y1", "x2", "y2"])
        bbox_by_idx = {d["idx"]: d["bbox"] for d in det}
        for idx, latex, bin_path in sorted(rec, key=lambda x: x[0]):
            x1, y1, x2, y2 = bbox_by_idx.get(idx, ("", "", "", ""))
            w.writerow([idx, latex, bin_path, x1, y1, x2, y2])
    print(f"[pipeline] CSV saved: {csv_path.resolve()}")

    formulas = [(idx, latex) for idx, latex, _ in rec]
    tex_path = write_latex_file(
        formulas=formulas,
        out_path=args.tex_out,
        title=f"Recognized Formulas — {img_name}",
    )
    print(f"[pipeline] TeX saved: {tex_path}")

    print("\n=== Итог ===")
    for idx, latex, bin_path in sorted(rec, key=lambda x: x[0]):
        print(f"({idx}) -> {latex}  [{bin_path}]")

if __name__ == "__main__":
    main()
