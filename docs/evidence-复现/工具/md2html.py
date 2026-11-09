#!/usr/bin/env python3
"""把 docs/ 下的复现指南 Markdown 转成可直接预览的 HTML（图片按相对路径内联引用）。

用法：python md2html.py               # 转换 docs 目录下所有 "0X-复现指南-*.md"
依赖：markdown（extensions: tables / fenced_code / toc / sane_lists）
"""
import glob
import os
import pathlib

import markdown

HERE = pathlib.Path(__file__).resolve().parent.parent.parent  # docs/
TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title}</title>
<style>
  :root {{ --fg:#1d2129; --muted:#4e5969; --line:#e5e6eb; --bg:#ffffff; --code:#f7f8fa; --accent:#165dff; }}
  * {{ box-sizing: border-box; }}
  body {{ margin:0; background:#f5f6f8; color:var(--fg);
         font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
         font-size:15px; line-height:1.75; }}
  .sheet {{ max-width: 960px; margin: 32px auto 64px; background:var(--bg);
            padding: 48px 56px 64px; border:1px solid var(--line); border-radius:12px; }}
  h1 {{ font-size:26px; line-height:1.4; margin:0 0 8px; }}
  h2 {{ font-size:20px; margin:40px 0 12px; padding-top:16px; border-top:1px solid var(--line); }}
  h3 {{ font-size:16px; margin:26px 0 10px; }}
  p {{ margin:10px 0; }}
  a {{ color:var(--accent); text-decoration:none; }}
  blockquote {{ margin:16px 0; padding:10px 16px; background:#f2f7ff; border-left:3px solid var(--accent);
                color:var(--muted); border-radius:0 6px 6px 0; }}
  blockquote p {{ margin:6px 0; }}
  code {{ background:var(--code); padding:2px 6px; border-radius:4px;
          font-family:ui-monospace,SFMono-Regular,Consolas,"Liberation Mono",monospace; font-size:13px; }}
  pre {{ background:var(--code); border:1px solid var(--line); border-radius:8px;
         padding:14px 16px; overflow-x:auto; margin:14px 0; }}
  pre code {{ background:none; padding:0; font-size:13px; line-height:1.6; }}
  table {{ border-collapse:collapse; width:100%; margin:16px 0; font-size:14px; }}
  th, td {{ border:1px solid var(--line); padding:8px 12px; text-align:left; vertical-align:top; }}
  th {{ background:#f7f8fa; font-weight:600; }}
  tr:nth-child(even) td {{ background:#fcfcfd; }}
  img {{ max-width:100%; border:1px solid var(--line); border-radius:8px; margin:12px 0 4px;
         box-shadow:0 2px 10px rgba(0,0,0,.06); display:block; }}
  hr {{ border:none; border-top:1px solid var(--line); margin:32px 0; }}
  ul, ol {{ padding-left:24px; }}
  li {{ margin:4px 0; }}
</style>
</head>
<body>
<div class="sheet">
{body}
</div>
</body>
</html>
"""


def convert(md_path: pathlib.Path) -> pathlib.Path:
    text = md_path.read_text(encoding="utf-8")
    body = markdown.markdown(
        text,
        extensions=["tables", "fenced_code", "sane_lists", "nl2br"],
        output_format="html5",
    )
    title = text.splitlines()[0].lstrip("# ").strip() if text.startswith("#") else md_path.stem
    out = md_path.with_suffix(".html")
    out.write_text(TEMPLATE.format(title=title, body=body), encoding="utf-8")
    return out


if __name__ == "__main__":
    targets = sorted(p for p in HERE.glob("*.md") if ("复现指南" in p.name or "新增功能" in p.name))
    for t in targets:
        p = convert(pathlib.Path(t))
        print("generated:", os.path.relpath(p, HERE))
