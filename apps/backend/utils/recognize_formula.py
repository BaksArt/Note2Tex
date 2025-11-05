import os

os.environ["TOKENIZERS_PARALLELISM"] = "false"
os.environ.setdefault("HF_HUB_OFFLINE", "1")

from pathlib import Path
from typing import List, Tuple, Optional
import numpy as np
import cv2
from PIL import Image
import torch
from transformers import TrOCRProcessor, VisionEncoderDecoderModel


def load_trocr(model_dir: str):
    device = "cuda" if torch.cuda.is_available() else "cpu"

    processor = TrOCRProcessor.from_pretrained(model_dir, local_files_only=True)
    model = VisionEncoderDecoderModel.from_pretrained(model_dir, local_files_only=True).to(device)
    model.eval()

    tok = processor.tokenizer
    if tok.pad_token is None:
        tok.pad_token = tok.eos_token
    if tok.bos_token_id is None and tok.cls_token_id is None:
        tok.add_special_tokens({"bos_token": "<s>"})
        model.decoder.resize_token_embeddings(len(tok))

    model.config.pad_token_id = tok.pad_token_id
    model.config.eos_token_id = tok.eos_token_id
    model.config.decoder_start_token_id = tok.bos_token_id or tok.cls_token_id or tok.eos_token_id

    torch.backends.cuda.matmul.allow_tf32 = True
    try:
        torch.set_float32_matmul_precision("high")
    except Exception:
        pass

    return processor, model, device


@torch.no_grad()
def recognize_one(processor, model, device, image: Image.Image,
                  max_new_tokens: int = 224, num_beams: int = 4, length_penalty: float = 1.1) -> str:
    inputs = processor(images=image, return_tensors="pt").to(device)
    out = model.generate(
        **inputs,
        max_new_tokens=max_new_tokens,
        num_beams=num_beams,
        length_penalty=length_penalty,
        no_repeat_ngram_size=2,
        early_stopping=True,
    )
    return processor.tokenizer.batch_decode(out, skip_special_tokens=True)[0].strip()


def otsu_binarize_pil(pil_img: Image.Image, strength: float = 1.2, erode_kernel: Optional[int] = None) -> Image.Image:
    gray = np.array(pil_img.convert("L"))
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    t, _ = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    adjusted_t = int(np.clip(int(t * strength), 0, 255))
    _, binary_adj = cv2.threshold(blur, adjusted_t, 255, cv2.THRESH_BINARY)

    if erode_kernel is not None and erode_kernel > 0:
        kernel = np.ones((erode_kernel, erode_kernel), np.uint8)
        binary_adj = cv2.erode(binary_adj, kernel, iterations=1)

    binary_rgb = cv2.cvtColor(binary_adj, cv2.COLOR_GRAY2RGB)
    return Image.fromarray(binary_rgb)


def recognize_crops(
        crop_paths: List[str],
        model_dir: str = None,
        beams: int = 4,
        max_new_tokens: int = 224,
        length_penalty: float = 1.1,
        bin_strength: float = 0.8,
        use_binarization: bool = True,
        erode_kernel: int = 3,
        out_dir: str = "temp",
) -> List[Tuple[int, str, str]]:
    processor, model, device = load_trocr(model_dir)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)


    results = []
    for p in crop_paths:
        pth = Path(p)
        try:
            idx = int(pth.stem.split("_")[-1])
        except Exception:
            idx = -1

        pil = Image.open(pth).convert("RGB")

        if use_binarization:
            processed_img = otsu_binarize_pil(pil, strength=bin_strength, erode_kernel=erode_kernel)
            prefix = "bin"
        else:
            processed_img = pil
            prefix = "orig"

        processed_path = out_dir / f"{prefix}_block_{idx:03d}.png"
        processed_img.save(processed_path)

        latex = recognize_one(processor, model, device, processed_img,
                              max_new_tokens=max_new_tokens,
                              num_beams=beams,
                              length_penalty=length_penalty)
        print(f"({idx}) -> {latex}")
        results.append((idx, latex, str(processed_path)))
    return results