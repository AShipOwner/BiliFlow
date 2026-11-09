# 抖音原包前端复用（douyin-spec）

来源：`aweme_43633312a_v5072_410001_9594_1793704170.apk`（`com.ss.android.ugc.aweme` 41.0.0，versionCode 410001，targetSdk 34，字节官方签名）
提取工具：`tools/extract_douyin_tokens.py`（Android build-tools 的 `aapt2` + Python zipfile）

## 包里到底有什么「前端源码」

| 形态 | 数量 | 可复用性 |
|---|---|---|
| `resources.arsc` 资源表 | 180,009 条（color 10,425 / dimen 4,158 / style 3,450 / layout 18,183 / drawable 31,730） | **高**：色值、间距、圆角、字阶是真实值，资源名被混淆成 `_`，但数值可直接用 |
| `r/*.xml` 编译后的布局 | 28,980 个 | **中**：`aapt2 dump xmltree` 可还原成可读树（dp/颜色/层级），但不是可直接编译的源码 |
| `assets/**/main.json` Lynx 模板 | 693 个 | **中高**：JSON 里就是元素树 + `style`（font_size / radius / padding / bg_color），属于真正的前端模板 |
| `res/font/douyin_number_*.otf` | 5 个 | **最高**：抖音自带的数字字体，已直接接入 App |
| `assets/` 图片/Lottie | 843 png + 547 lottie json | 中：Lottie 动画可直接用 |

APK 里没有「一份可以整体搬走的工程源码」：布局是编译后的二进制 XML，UI 逻辑在混淆后的 dex/Lynx 运行时里，很多页面还是服务端下发模板。可复用且不破坏功能的是**设计令牌 + 字体 + 模板结构**。

## 已落地到 BiliFlow 的部分

1. **色板**（`ui/DouyinTokens.kt` → `DouyinColor`），取资源表引用次数最高的真实色值：
   - `#161823`（621 次，主文本/遮罩基色）、`#FE2C55`（303 次，品牌红）、`#FACE15`（128 次，高亮黄）、
     `#161616` / `#262626` / `#393B44`（面层）、`#168EF9` / `#04498D`（链接蓝）、`#F3F3F4` / `#F8F8F8`（浅色底）。
   - `Theme.kt` 的 `BiliAccent/BiliSurface/BiliSurfaceRaised/...` 现在全部指向这些令牌，不再手写色值。
2. **间距**（`DouyinSpace`）：资源表里出现最多的 dp 值 16/8/12/4/20/2/44/24/32/48。
3. **圆角**（`DouyinRadius`）：Lynx 模板 radius 统计 8（257 次）/12（127）/10（115）/2/4/6/100。
4. **字阶**（`DouyinType`）：Lynx 模板 font_size 统计 12/14/13/10/11/16/15/17。
5. **数字字体**：`res/font/douyin_number_{medium,bold,extra_bold,condensed_medium,extra_condensed_medium}.otf`
   → `DouyinNumber` / `DouyinNumberCondensed`，已用于点赞/评论/投币/收藏/分享计数与播放量文案。

## 代表布局

`layouts/common_feed_bottom_view_layout.xml.txt` —— 抖音 feed 底部条的真实层级：
外层 `RelativeLayout` 12dp padding，条高 `40dp`，内部 20dp 头像 + 4dp 圆角、16dp 播放态图标，
`ImageView` 2 层叠放做进度/遮罩。这和 BiliFlow 的 `VideoInfo` + `DanmakuBar` 区域一一对应。

## 重新提取

```bash
python tools/extract_douyin_tokens.py \
  --apk "C:/Users/Administrator/Desktop/aweme_43633312a_v5072_410001_9594_1793704170.apk" \
  --out douyin-spec
```

输出 `tokens.json`（颜色/间距/字阶统计）、`fonts/`、`layouts/`，并把字体同步进 `app/src/main/res/font/`。

## 不能直接照搬的部分

- **布局 XML**：资源 ID 与 `R` 绑定在 `com.ss.android.ugc.aweme` 包里，搬进别的 package 无法解析；
  只能按解码出的 dp/层级在 Compose 里重建（本项目就是这么做的）。
- **Lynx 模板**：需要 Lynx 运行时（`liblynx`），且模板绑定抖音服务端数据协议。
- **图标**：大多被混淆成 `r/*.webp`，名字不可读；能对上语义的只有少数 SDK 图标，因此 UI 图标仍用自绘/系统图标。
