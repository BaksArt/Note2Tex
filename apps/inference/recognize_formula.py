import os
os.environ["TOKENIZERS_PARALLELISM"] = "false"
os.environ.setdefault("HF_HUB_OFFLINE", "1")

from pathlib import Path
from typing import List, Tuple
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
def recognize_one(processor, model, device, image_path: str,
                  max_new_tokens: int = 224, num_beams: int = 4, length_penalty: float = 1.1) -> str:
    img = Image.open(image_path).convert("RGB")
    inputs = processor(images=img, return_tensors="pt").to(device)
    out = model.generate(
        **inputs,
        max_new_tokens=max_new_tokens,
        num_beams=num_beams,
        length_penalty=length_penalty,
        no_repeat_ngram_size=2,
        early_stopping=True,
    )
    return processor.tokenizer.batch_decode(out, skip_special_tokens=True)[0].strip()

def otsu_binarize_pil(pil_img: Image.Image, strength: float = 1.2) -> Image.Image:
    gray = np.array(pil_img.convert("L"))
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    t, _ = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    adjusted_t = int(np.clip(int(t * strength), 0, 255))
    _, binary_adj = cv2.threshold(blur, adjusted_t, 255, cv2.THRESH_BINARY)
    return Image.fromarray(binary_adj)

def recognize_crops(
    crop_paths: List[str],
    model_dir: str = "models/trocr_latex_fast",
    beams: int = 4,
    max_new_tokens: int = 224,
    length_penalty: float = 1.1,
    bin_strength: float = 1.2,
    out_dir: str = "temp",
) -> List[Tuple[int, str, str]]:
    """
    Возвращает список кортежей: (idx, latex, bin_path)
    где idx — номер из имени файла raw_block_XXX.png
    """
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
        bin_img = otsu_binarize_pil(pil, strength=bin_strength)

        bin_path = out_dir / f"block_{idx:03d}.png"
        bin_img.save(bin_path)

        latex = recognize_one(processor, model, device, str(bin_path),
                              max_new_tokens=max_new_tokens,
                              num_beams=beams,
                              length_penalty=length_penalty)
        print(f"({idx}) -> {latex}")
        results.append((idx, latex, str(bin_path)))
    return results

if __name__ == "__main__":
    raw = sorted(str(p) for p in Path("temp").glob("raw_block_*.png"))
    recognize_crops(raw, model_dir="models/trocr_latex_fast", bin_strength=1.2)
