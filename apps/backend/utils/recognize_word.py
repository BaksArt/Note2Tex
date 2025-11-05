# app/utils/recognize_word.py
from __future__ import annotations
import math, os
from typing import Optional, Tuple, Union, List
from pathlib import Path
from torch.nn import functional as F
import numpy as np
import cv2
from PIL import Image

import torch
from torch import nn
from torch.nn import Conv2d, MaxPool2d, BatchNorm2d, LeakyReLU
from torchvision import transforms

# ====== params ======
WIDTH = 256
HEIGHT = 64
CHANNELS = 1

ALPHABET: List[str] = [
    'PAD', 'SOS', ' ', '!', '"', '%', '(', ')', ',', '-', '.', '/',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '?',
    '[', ']', '«', '»', 'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'З', 'И',
    'Й', 'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С', 'Т', 'У', 'Ф', 'Х',
    'Ц', 'Ч', 'Ш', 'Щ', 'Э', 'Ю', 'Я', 'а', 'б', 'в', 'г', 'д', 'е',
    'ж', 'з', 'и', 'й', 'к', 'л', 'м', 'н', 'о', 'п', 'р', 'с', 'т',
    'у', 'ф', 'х', 'ц', 'ч', 'ш', 'щ', 'ъ', 'ы', 'ь', 'э', 'ю', 'я',
    'ё', 'EOS'
]

HIDDEN = 512
ENC_LAYERS = 2
DEC_LAYERS = 2
N_HEADS = 4
DROPOUT = 0.2

# ====== utils ======
def indicies_to_text(indexes: list, idx2char: List[str]) -> str:
    text = "".join(idx2char[i] for i in indexes)
    return text.replace('EOS', '').replace('PAD', '').replace('SOS', '').strip()

def process_image(img: np.ndarray) -> np.ndarray:
    w, h, _ = img.shape
    new_w = HEIGHT
    new_h = int(h * (new_w / w))
    img = cv2.resize(img, (new_h, new_w))
    w, h, _ = img.shape
    img = img.astype('float32')
    new_h = WIDTH
    if h < new_h:
        add_zeros = np.full((w, new_h - h, 3), 255)
        img = np.concatenate((img, add_zeros), axis=1)
    if h > new_h:
        img = cv2.resize(img, (new_h, new_w))
    return img

def _to_pil(img: Union[str, Image.Image, np.ndarray]) -> Image.Image:
    if isinstance(img, str):
        return Image.open(img).convert("RGB")
    if isinstance(img, Image.Image):
        return img.convert("RGB")
    if isinstance(img, np.ndarray):
        arr = img
        if arr.ndim == 2: return Image.fromarray(arr).convert("RGB")
        if arr.ndim == 3:
            if arr.dtype != np.uint8: arr = np.clip(arr, 0, 255).astype(np.uint8)
            if arr.shape[2] == 3:  return Image.fromarray(arr, mode="RGB")
            if arr.shape[2] == 4:  return Image.fromarray(arr, mode="RGBA").convert("RGB")
            return Image.fromarray(arr[:, :, :3]).convert("RGB")
    raise ValueError("Unsupported image input")

def _prep_tensor_for_model(pil_img: Image.Image) -> torch.Tensor:
    img = np.asarray(pil_img).astype("uint8")
    img = process_image(img).astype("uint8")  # (64,256,3)
    assert img.shape[:2] == (HEIGHT, WIDTH), f"Got {img.shape[:2]}, expected {(HEIGHT, WIDTH)}"
    denom = max(1, int(img.max()))
    img = img / denom
    img = np.transpose(img, (2, 0, 1))        # (C,H,W)
    src = torch.from_numpy(img).float().unsqueeze(0)  # (1,C,H,W)
    if CHANNELS == 1:
        src = transforms.Grayscale(CHANNELS)(src)
    return src

# ====== model ======
class PositionalEncoding(nn.Module):
    def __init__(self, d_model: int, dropout: float = 0.1, max_len: int = 5000):
        super().__init__()
        self.dropout = nn.Dropout(p=dropout)
        self.scale = nn.Parameter(torch.ones(1))
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float32).unsqueeze(1)
        div = torch.exp(torch.arange(0, d_model, 2, dtype=torch.float32) * (-math.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div)
        pe[:, 1::2] = torch.cos(position * div)
        pe = pe.unsqueeze(0).transpose(0, 1)
        self.register_buffer('pe', pe)
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.dropout(x + self.scale * self.pe[:x.size(0), :])

class TransformerModel(nn.Module):
    def __init__(self, outtoken: int, hidden: int, enc_layers: int = 1, dec_layers: int = 1,
                 nhead: int = 1, dropout: float = 0.1):
        super().__init__()
        self.conv0 = Conv2d(1, 64, 3, 1, 1)
        self.conv1 = Conv2d(64, 128, 3, 1, 1)
        self.conv2 = Conv2d(128, 256, 3, (2, 1), 1)
        self.conv3 = Conv2d(256, 256, 3, 1, 1)
        self.conv4 = Conv2d(256, 512, 3, (2, 1), 1)
        self.conv5 = Conv2d(512, 512, 3, 1, 1)
        self.conv6 = Conv2d(512, 512, (2,1), 1)

        self.pool1 = MaxPool2d(2,2, 0)
        self.pool3 = MaxPool2d(2,2, 0)
        self.pool5 = MaxPool2d((2,2), (2,1), (0,1))

        self.bn0 = BatchNorm2d(64);  self.bn1 = BatchNorm2d(128)
        self.bn2 = BatchNorm2d(256); self.bn3 = BatchNorm2d(256)
        self.bn4 = BatchNorm2d(512); self.bn5 = BatchNorm2d(512); self.bn6 = BatchNorm2d(512)

        self.activ = LeakyReLU()
        self.pos_encoder = PositionalEncoding(hidden, dropout)
        self.decoder = nn.Embedding(outtoken, hidden)
        self.pos_decoder = PositionalEncoding(hidden, dropout)
        self.transformer = nn.Transformer(
            d_model=hidden, nhead=nhead, num_encoder_layers=enc_layers,
            num_decoder_layers=dec_layers, dim_feedforward=hidden*4,
            dropout=dropout, batch_first=False
        )
        self.fc_out = nn.Linear(hidden, outtoken)
        self.src_mask = self.trg_mask = self.memory_mask = None

    def _get_features(self, src: torch.Tensor) -> torch.Tensor:
        x = self.activ(self.bn0(self.conv0(src)))
        x = self.pool1(self.activ(self.bn1(self.conv1(x))))
        x = self.activ(self.bn2(self.conv2(x)))
        x = self.pool3(self.activ(self.bn3(self.conv3(x))))
        x = self.activ(self.bn4(self.conv4(x)))
        x = self.pool5(self.activ(self.bn5(self.conv5(x))))
        x = self.activ(self.bn6(self.conv6(x)))

        assert x.shape[2] == 1, f"Feature height must be 1, got {x.shape[2]}"
        x = x.permute(0, 3, 1, 2).flatten(2).permute(1, 0, 2)
        return x

def load_htr_model(weights_path: str,
                   hidden: int = HIDDEN, enc_layers: int = ENC_LAYERS,
                   dec_layers: int = DEC_LAYERS, nhead: int = N_HEADS,
                   dropout: float = DROPOUT, device: Optional[str] = None) -> Tuple[nn.Module, str]:
    dev = device or ("cuda" if torch.cuda.is_available() else "cpu")
    m = TransformerModel(len(ALPHABET), hidden, enc_layers, dec_layers, nhead, dropout).to(dev)
    state = torch.load(weights_path, map_location=dev)
    m.load_state_dict(state)
    m.eval()
    torch.backends.cuda.matmul.allow_tf32 = True
    try: torch.set_float32_matmul_precision("high")
    except Exception: pass
    return m, dev

@torch.no_grad()
def _greedy_decode(model: nn.Module, src: torch.Tensor, device: str, max_len: int = 100):
    src = src.to(device)
    x = model._get_features(src)
    try:
        memory = model.transformer.encoder(model.pos_encoder(x))  # <-- используем model.pos_encoder
    except Exception as e:
        import traceback
        print("ENCODER CRASH:", e)
        print(traceback.format_exc())
    sos_id = ALPHABET.index("SOS"); eos_id = ALPHABET.index("EOS")
    out_indexes = [sos_id]; logps = []
    for _ in range(max_len):
        trg_tensor = torch.LongTensor(out_indexes).unsqueeze(1).to(device)  # (T,1)
        dec_inp = model.pos_decoder(model.decoder(trg_tensor))              # <-- и model.pos_decoder
        dec_out = model.transformer.decoder(dec_inp, memory)
        last_logits = model.fc_out(dec_out)[-1, 0, :]
        probs = torch.softmax(last_logits.float(), dim=-1)
        out_token = int(torch.argmax(probs).item())
        out_indexes.append(out_token)
        pmax = float(torch.max(probs).item())
        if out_token not in (sos_id, eos_id) and pmax > 0:
            logps.append(math.log(pmax + 1e-12))
        if out_token == eos_id:
            break
    text = indicies_to_text(out_indexes, ALPHABET)
    conf = float(np.exp(np.mean(logps))) if logps else 0.0
    print(f'text recognized: {text} (conf: {conf})')
    return text, conf

def recognize_word(img: Union[str, Image.Image, np.ndarray],
                   weights_path: str = None,
                   model: Optional[nn.Module] = None,
                   device: Optional[str] = None,
                   max_len: int = 100) -> Tuple[str, float]:
    if model is None:
        assert weights_path and os.path.exists(weights_path), "weights not found for words ocr"
        model, dev = load_htr_model(weights_path, device=device)
    else:
        dev = device or ("cuda" if torch.cuda.is_available() else "cpu")
        model = model.to(dev); model.eval()

    pil = _to_pil(img)
    src = _prep_tensor_for_model(pil)
    return _greedy_decode(model, src, dev, max_len=max_len)
