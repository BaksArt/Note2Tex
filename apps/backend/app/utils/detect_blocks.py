import os
from pathlib import Path
import shutil
import cv2
import torch
from ultralytics import YOLO

ID2NAME = {0: "formula", 1: "other", 2: "table", 3: "text_line"}


def _xyxy_to_int_box(xyxy, W, H, pad=0.0):
    x1, y1, x2, y2 = xyxy
    w = x2 - x1
    h = y2 - y1
    pad_px = int(max(w, h) * pad)
    xi = max(0, int(x1) - pad_px)
    yi = max(0, int(y1) - pad_px)
    xa = min(W, int(x2) + pad_px)
    ya = min(H, int(y2) + pad_px)
    return xi, yi, xa, ya


def _sort_boxes_tblr(boxes):
    return sorted(boxes, key=lambda b: (b[1] // 50, b[1], b[0]))


def detect_blocks(
        image_path: str,
        yolo_weights: str = "models/detector/best.pt",
        conf: float = 0.25,
        iou: float = 0.5,
        imgsz: int = 1280,
        pad: float = 0.04,
        temp_dir: str = "temp",
        save_viz: bool = True,
):
    src = Path(image_path)
    assert src.exists(), f"File not found: {src}"

    tdir = Path(temp_dir)
    if tdir.exists():
        shutil.rmtree(tdir)
    (tdir).mkdir(parents=True, exist_ok=True)

    page = cv2.imread(str(src), cv2.IMREAD_COLOR)
    assert page is not None, f"Cannot read image: {src}"
    H, W = page.shape[:2]

    device = "0" if torch.cuda.is_available() else "cpu"
    model = YOLO(yolo_weights)
    model.model.names = ID2NAME

    rlist = model.predict(
        source=str(src),
        conf=conf,
        iou=iou,
        imgsz=imgsz,
        device=device,
        save=False,
        classes=[0, 3],
        verbose=False
    )
    r = rlist[0]

    boxes = []
    if r.boxes is not None and len(r.boxes):
        for b in r.boxes:
            xyxy = [float(x) for x in b.xyxy[0].tolist()]
            cls_id = int(b.cls.item()) if b.cls is not None else 0
            conf_score = float(b.conf.item()) if b.conf is not None else 0.0
            name = ID2NAME.get(cls_id, "formula")

            if name == "text_line" and conf_score < 0.5:
                continue

            xi, yi, xa, ya = _xyxy_to_int_box(xyxy, W, H, pad=pad)
            boxes.append((xi, yi, xa, ya, name))

    if not boxes:
        print("[detect] No blocks found")
        return []

    boxes = _sort_boxes_tblr(boxes)

    results = []
    page_viz = page.copy()
    for idx, (x1, y1, x2, y2, name) in enumerate(boxes, start=1):
        crop = page[y1:y2, x1:x2, :]
        out_path = tdir / f"raw_block_{idx:03d}.png"
        cv2.imwrite(str(out_path), crop)
        results.append({"idx": idx, "bbox": (x1, y1, x2, y2), "crop_path": str(out_path), "cls": name})
        if save_viz:
            color = (0, 255, 0) if name == "formula" else (255, 0, 0)
            cv2.rectangle(page_viz, (x1, y1), (x2, y2), color, 2)
            cv2.putText(page_viz, f"{idx}:{name}", (x1, max(0, y1 - 5)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2, cv2.LINE_AA)
    if save_viz:
        viz_path = src.with_name(src.stem + "_boxes.png")
        cv2.imwrite(str(viz_path), page_viz)
        print(f"[detect] viz saved: {viz_path}")

    print(f"[detect] {len(results)} blocks, crops in: {Path(temp_dir).resolve()}")
    return results
