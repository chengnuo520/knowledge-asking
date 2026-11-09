## 渲染自检清单

这是一段带 **粗体**、*斜体* 和 `行内代码` 的普通段落。

- 无序列表项 A
- 无序列表项 B

1. 有序列表项一
2. 有序列表项二

```java
int sum = a + b;   // 代码块内的 **双星号** 与 *斜体* 不应被渲染
```

> 引用一段话，验证 blockquote 分支。

恶意输入测试：<img src=x onerror=alert('xss')> 和 <script>alert(1)</script> 必须显示成文本。
