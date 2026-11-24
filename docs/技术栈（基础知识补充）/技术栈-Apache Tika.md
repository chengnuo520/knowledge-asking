# 技术栈说明：Apache Tika

> 版本：3.2.3（`tika-core` + `tika-parsers-standard-package`）
> 定位：**文档格式探测与内容抽取的"总闸"**
> 本文结构：一、它是什么｜二、在本项目中的体现｜三、学习计划｜四、什么时候不该用｜五、测试题

---

## 一、它是什么

### 1.1 一句话定位

Tika 解决的是**格式矩阵爆炸**问题：你有 N 种文档格式要读，不想写 N 个解析器，就用一个库把「这是什么格式」和「怎么把文字抠出来」统一掉。

```
任意文件 → [ Detector 判断 MIME ] → [ Parser 抽取内容 ] → 纯文本 / XHTML / 元数据
```

### 1.2 它到底替你做了什么

| 你自己做 | 用 Tika |
|---|---|
| 识别 PDF 的 `%PDF-` 魔数、docx 是个 zip、OOXML 的 `[Content_Types].xml` | 一行 `detect()` |
| PDF 用 PDFBox 逐页取文本、处理字体编码与 CID | 已封装 |
| docx 解 zip、解析 `word/document.xml`、拼段落 | 已封装 |
| 老式 `.doc` 的 OLE 复合文档、`.rtf` 的控制字 | 已封装 |
| 加密文档、损坏文件的异常处理 | 统一成 `TikaException` |

**核心价值不是"能读"，而是"读得一样"**——同一套 API 面对几百种格式，输出统一。

### 1.3 三个必须分清的概念

| 概念 | 作用 | 对应接口 |
|---|---|---|
| **Detector** | 判断 MIME 类型（看魔数、看容器结构） | `org.apache.tika.detect.Detector` |
| **Parser** | 按 MIME 选解析器，把内容写进 `ContentHandler` | `org.apache.tika.parser.Parser`（组合器是 `AutoDetectParser`） |
| **ContentHandler** | 决定"输出成什么"：纯文本？XHTML？只取元数据？ | `BodyContentHandler` / `ToXMLContentHandler` / `TOCTextContentHandler` |

**这三者的组合才是 Tika 的全部可调空间**。很多人只用了 `Tika` 门面类，于是误以为 Tika 只能出纯文本——实际是门面替你选了一个最简单的 ContentHandler。

### 1.4 两个依赖分别干什么

```xml
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-core</artifactId>              <!-- 只有探测 + 接口，约 700KB -->
</dependency>
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-parsers-standard-package</artifactId>  <!-- 真正的格式解析器包，几十 MB -->
</dependency>
```

只做「判断文件类型」时，只引 `tika-core` 就够；一旦要抽内容，就必须引 `parsers-standard-package`，它会把 PDFBox、POI、jackcess 等一堆库全部拉进来——**这是 Tika 的主要体积成本**。

### 1.5 能力边界（它做不到什么）

| 做不到 | 说明 |
|---|---|
| OCR | 扫描版 PDF / 图片里的字抽不出，需要 Tesseract |
| 版面还原 | 拿不到"这段是标题、那段是表格"的语义（除非用 XHTML 模式） |
| 表格结构 | 纯文本模式下表格退化成用空格/换行分隔的文本 |
| 图表内容 | 图片里的信息完全丢失 |
| 语义理解 | 它只负责抽取，不负责分块与检索 |

---

## 二、在本项目中的体现

Tika 在项目里是**双角色**，这是最容易被说错的地方。

### 2.1 角色一：探测所有上传文件

`MimeTypeDetector` 用 Tika 判断 MIME，**所有格式都过一遍**，不只是 PDF：

```java
private static final Tika TIKA = new Tika();

public String detect(byte[] bytes, String filename) {
    String detected = filename == null || filename.isBlank()
            ? TIKA.detect(bytes)
            : TIKA.detect(bytes, filename);
    ...
}
```

为什么不能直接信任浏览器给的 `Content-Type`？因为它是客户端说了算：把 `.exe` 改名成 `.txt` 上传，`Content-Type` 依然会写 `text/plain`。所以**以文件内容为准，扩展名只作兜底**。

项目里还加了两层纠正（这两层是实战经验，不是 Tika 教的）：

```java
// ① Tika 判不出来（blank / octet-stream）→ 用扩展名兜底
if (byExtension != null && (detected.isBlank() || "application/octet-stream".equals(detected))) {
    detected = byExtension;
}
// ② Tika 说是 text/plain，但扩展名是 md/csv → 按扩展名纠正
else if ("text/plain".equals(detected) && ("text/markdown".equals(byExtension) || "text/csv".equals(byExtension))) {
    detected = byExtension;
}
```

第 ② 条是必需的：Markdown 和 CSV 本质就是文本，Tika 会如实报告 `text/plain`。若不纠正，`.csv` 会被 `PlainTextDocumentParser` 接走，表格结构直接消失。

### 2.2 角色二：解析 4 种格式

`TikaDocumentParser` 只认领 4 个 MIME：

```java
@Override
public Set<String> supportedMimeTypes() {
    return Set.of(
            "application/pdf",
            "application/msword",                                              // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/rtf");
}
```

解析实现非常短：

```java
String text = TIKA.parseToString(input);              // ← 门面方法
for (String segment : text.split("\\r?\\n\\s*\\r?\\n")) {
    if (!segment.isBlank()) blocks.add(new ParagraphBlock(segment.strip()));
}
```

**这里有一个必须讲清楚的设计事实**：`Tika.parseToString()` 内部是 `AutoDetectParser + BodyContentHandler`，输出是**纯文本**。所以 docx 的标题、表格、列表全部退化成普通段落，只产出 `ParagraphBlock`。

这不是 Tika 的问题，是我们选了门面方法。改进路径在 3.3 说明。

### 2.3 路由结果（启动日志可验证）

```
RAG parser routes: application/pdf        -> tika
RAG parser routes: application/msword     -> tika
RAG parser routes: application/vnd.openxmlformats...document -> tika
RAG parser routes: application/rtf        -> tika
RAG parser routes: text/markdown          -> markdown
RAG parser routes: text/x-markdown        -> markdown
RAG parser routes: text/csv               -> csv
RAG parser routes: text/plain             -> plaintext
```

`ParserRegistry` 在构造时校验：同一个 MIME 被两个解析器认领会**启动即失败**，避免"悄悄走错解析器"。

### 2.4 实测耗时（同一台机器）

| 文件 | 解析器 | 大小 | 解析耗时 |
|---|---|---|---|
| `01-产品保修政策.md` | commonmark | 1.4KB | 56ms（首次含 Tika 初始化 201ms） |
| `12-长扩展名.markdown` | commonmark | 0.3KB | 26ms |
| `06-员工手册.docx` | **Tika** | 1.6KB | **781ms** |
| `11-产品说明书.pdf` | **Tika** | 116KB | **582ms** |

结论：Tika 的启动与解析成本比专用解析器**高一个数量级**（几百毫秒 vs 几十毫秒）。这是引入它的代价，但要自己写 PDF 解析显然不划算。

### 2.5 两个真实缺陷

**① docx / pdf 拿不到章节路径**

实测 `11-产品说明书.pdf` 的块：`charCount=397，outlinePath=[]`。而 `01-....md` 的块是 `outlinePath=["产品保修政策"]`。

原因就是 2.2 说的：纯文本输出没有层级信息，`HeadingHandler` 无标题可用，路径自然为空。

**② 大文档可能被静默截断**

`Tika` 门面类的 `parseToString()` 有默认字符串上限（`DEFAULT_MAX_STRING_LENGTH = 100000` 字符）。超过这个长度的 PDF 会被**静默截断**，不报错。修法是显式放开：

```java
Tika tika = new Tika();
tika.setMaxStringLength(-1);   // -1 = 不限制（要自己控内存）
```

本项目目前是 default，所以**大 PDF 存在截断风险**——这是一条应该写进"已知问题"的项。

---

## 三、学习计划

按四周推进，每阶段都有可验收的产物，不是"看完文档就算学会"。

### 第 1 周：API 三件套

| 目标 | 具体动作 | 验收物 |
|---|---|---|
| 分清 Detector / Parser / ContentHandler | 手写一个 `AutoDetectParser` + `BodyContentHandler` 替代门面 | 能说出三者的调用顺序 |
| 理解 MIME 体系 | 用 Tika 探测 10 个不同后缀的文件（含伪造后缀） | 一张"实际 MIME vs 扩展名"的对照表 |
| 知道门面类的边界 | 读 `Tika.parseToString` 源码，找到 `maxStringLength` | 写一段"为什么大文件会截断"的结论 |

### 第 2 周：输出格式的差异

| 目标 | 具体动作 | 验收物 |
|---|---|---|
| 理解 ContentHandler 决定一切 | 同一份 docx，分别用 `BodyContentHandler`、`ToXMLContentHandler`、`XHTMLContentHandler` 输出 | 三份输出对比，标出结构信息差在哪 |
| 验证结构能否救回来 | 用 XHTML 模式解析带标题的 docx，看 `<h1>` 是否出现 | 结论：XHTML 模式能保留标题/表格标签 |

这一周是**认知转折点**：会明白"Tika 丢结构"其实是"你选的 handler 丢结构"。

### 第 3 周：用专用解析器替换 Tika

| 格式 | 方案 | 关键点 |
|---|---|---|
| docx | Apache POI `XWPFDocument` | 遍历 `XWPFParagraph`，读 `getStyle()` 判断 Heading1/2，映射成 `HeadingBlock` |
| docx 表格 | `XWPFTable` | 转成 `TableBlock`，保留表头 |
| pdf | PDFBox `PDFTextStripper` 子类 | 重写 `writeString`，用字号 / 粗体 / 行高判断标题层级 |

验收物：同一份 docx 用 Tika 与 POI 各解析一次，**对比 `outlinePath` 是否非空**。

### 第 4 周：性能与安全（容易被忽略但面试常问）

| 主题 | 要做的事 |
|---|---|
| 解析超时 | 用 `ParseContext` 设置超时，防止畸形文件卡死线程 |
| 内存上限 | 限制单文件大小 + 堆外内存；超大文档走流式而非 `byte[]` |
| 压缩炸弹 | docx/xlsx 本质是 zip，要限制解压后的总体积 |
| XXE | 解析 XML 类格式时禁用外部实体 |
| 并发 | `Tika` 实例是否线程安全（门面类通常可复用，但 `Parser` 需注意状态） |

验收物：写一组"恶意样本"（超大解压比、嵌套 zip、损坏 PDF），确认服务不崩、返回 400。

### 学习资源优先级

1. 官方文档的 **Parser 与 Detector** 两章（其余章节用到再查）
2. `AutoDetectParser` 的源码（200 行左右，读完就懂路由）
3. PDFBox 的 `PDFTextStripper`（做 PDF 结构化的入口）
4. 不必通读格式列表——Tika 支持几百种格式，记住"有 Parser 接口可查"即可

---

## 四、什么时候不该用 Tika

| 场景 | 更好的选择 |
|---|---|
| 只需要解析一种格式，且要求高保真结构 | 直接用该格式的专用库（POI / PDFBox / commonmark） |
| 扫描件为主 | Tika + Tesseract（OCR），或云文档智能服务 |
| 极简部署（镜像要小） | 只引 `tika-core` 做探测，解析交给专用库 |
| 需要表格语义 | Tika 的 XHTML 模式 + 自写表格抽取器，或 `tabula` / `camelot` |

一句话：**Tika 是"广度"工具，不是"深度"工具。** 它保证你用一份代码吃下所有格式，但每种格式的上限由专用库决定。

---

## 五、测试题

### 5.1 基础自检

<details>
<summary><b>Q1. 项目里 Tika 用在几处？分别是什么角色？</b></summary>
<p>两处。① <code>MimeTypeDetector</code>：对<strong>所有</strong>上传文件做 MIME 探测；② <code>TikaDocumentParser</code>：只解析 pdf/doc/docx/rtf 四种格式。</p>
</details>

<details>
<summary><b>Q2. 为什么 <code>.md</code> 明明有专用解析器，还要过 Tika？</b></summary>
<p>因为&quot;探测&quot;和&quot;解析&quot;是两件事。探测必须看内容（防伪造后缀），这个能力由 Tika 提供；解析才按 MIME 路由到 commonmark。</p>
</details>

<details>
<summary><b>Q3. <code>Tika.parseToString()</code> 输出的是什么？为什么 docx 会丢标题？</b></summary>
<p>纯文本。它内部用 <code>BodyContentHandler</code>，只保留文字流，不保留层级与表格结构；我们的 <code>TikaDocumentParser</code> 进而把文本按空行切成 <code>ParagraphBlock</code>，标题信息彻底丢失。</p>
</details>

<details>
<summary><b>Q4. 如果把 <code>.csv</code> 交给 Tika 解析，会发生什么？</b></summary>
<p>表格退化成纯文本行，<code>TableBlock</code> 不存在了，<code>rows-per-chunk</code> 参数失效，块里也不再有表头重复。这正是必须用专用 CSV 解析器的原因。</p>
</details>

<details>
<summary><b>Q5. Tika 解析 116KB 的 PDF 要 582ms，而 md 只要 26ms，差在哪？</b></summary>
<p>PDF 要先解析交叉引用表、解压内容流、重建字体与编码映射，再逐字符抽取；Markdown 只是语法扫描。成本主要来自 PDF 格式本身，不是 Tika 的实现质量。</p>
</details>

### 5.2 面试延伸

<details>
<summary><b>Q6. Tika 的 Detector 和 Parser 有什么区别？为什么可以分开？</b></summary>
<p>Detector 只回答&quot;这是什么&quot;，Parser 回答&quot;怎么读&quot;。分开的好处是可以先探测再按 MIME 路由（微服务/插件化），也允许只引 <code>tika-core</code> 做轻量探测而不带几十 MB 的解析器包。</p>
</details>

<details>
<summary><b>Q7. 文件上传时如何防止伪造 Content-Type？</b></summary>
<p>不信任请求头，读取字节流的魔数/容器结构判断（Tika 的 Detector 就是干这个），再用扩展名做二级校验，两者不一致时以内容为准并记录告警。</p>
</details>

<details>
<summary><b>Q8. 为什么 <code>ContentHandler</code> 的选择比 <code>Parser</code> 更影响结果？</b></summary>
<p>Parser 决定&quot;能不能读&quot;，ContentHandler 决定&quot;读出来是什么形状&quot;。同样的 docx，<code>BodyContentHandler</code> 得到纯文本，<code>XHTMLContentHandler</code> 得到带 <code>&lt;h1&gt;</code>/<code>&lt;table&gt;</code> 的结构化流——后续能否做结构化分块，全看这一步。</p>
</details>

<details>
<summary><b>Q9. 解析用户上传文件有哪些安全风险？</b></summary>
<p>① 压缩炸弹（docx 解压后暴涨）；② XXE（XML 外部实体读本地文件）；③ 解析器漏洞（历史 PDFBox/POI 有 CVE）；④ 超时/内存耗尽（畸形文件）。对策：限大小、限解压比、禁外部实体、设解析超时、及时升级依赖。</p>
</details>

<details>
<summary><b>Q10. 如果让你把 docx 的标题结构救回来，怎么做？</b></summary>
<p>用 POI 的 <code>XWPFDocument</code> 遍历段落，<code>paragraph.getStyle()</code> 返回 <code>Heading1/2/3</code> 时产出 <code>HeadingBlock</code>，正文产出 <code>ParagraphBlock</code>，表格用 <code>XWPFTable</code> 转 <code>TableBlock</code>。核心是把&quot;文本流&quot;换成&quot;带类型的块序列&quot;，这样 <code>HeadingHandler</code> 才能维护 <code>outlinePath</code>。</p>
</details>
