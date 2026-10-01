# AdGuard / uBlock Origin / Adblock Plus 过滤规则语法对照

> 基准日期：2026-09-10  
> 范围：仅比较过滤规则语言及其语义对应关系；不讨论 Safari/iOS、Manifest V2/V3 等宿主平台差异。  
> 原则：严格区分“语法兼容”“功能等价”“单向兼容”“引擎专有”。

## 1. 兼容等级

| 等级 | 定义 | 判定标准 |
|---|---|---|
| **A — 同构兼容** | 三者使用相同语法，且核心语义一致 | 可原样复用 |
| **B — 双边兼容** | 两个引擎采用相同语法，或官方明确兼容 | 可在对应两引擎之间直接复用 |
| **C — 功能对应** | 能实现相同目标，但语法、执行层或调用协议不同 | 必须转换 |
| **D — 引擎专有** | 其他引擎不存在可靠静态规则等价物 | 必须保留专用规则 |
| **△ — 语义差异** | 名称相同或相近，但行为、作用域或参数语义不完全一致 | 逐条验证 |

---

## 2. 基础网络匹配语法

| 功能 | AdGuard | uBlock Origin | Adblock Plus | 等级 | 说明 |
|---|---|---|---|---|---|
| 普通 URL 子串匹配 | `foo/bar` | `foo/bar` | `foo/bar` | △ | uBO 对可解析为合法主机名的裸字符串有特殊 HOSTS 语义；跨端规则不应使用裸主机名。 |
| 通配符 | `*` | `*` | `*` | A | 匹配任意字符序列。 |
| URL 起止锚点 | `|` | `|` | `|` | A | 位于规则首尾时约束 URL 起点/终点。 |
| 主机名锚点 | `||` | `||` | `||` | A | 域名阻止的标准公共写法。 |
| URL 分隔符 | `^` | `^` | `^` | A | 匹配 URL 分隔符或 URL 末尾。 |
| 正则网络规则 | `/regexp/` | `/regexp/` | `/regexp/` | A | 三者均支持正则网络过滤。 |
| 网络例外 | `@@` | `@@` | `@@` | A | 允许匹配请求。 |
| 修饰符起始 | `$` | `$` | `$` | A | 网络规则 modifier/options 起始符。 |
| 多修饰符组合 | `$script,image` | `$script,image` | `$script,image` | A | 逗号分隔。 |
| 反向修饰符 | `~script` | `~script` | `~script` | A | 排除指定类型或条件。 |
| 大小写敏感 | `$match-case` | `$match-case` | `$match-case` | A | 对 URL 模式启用大小写敏感匹配。 |

### 裸主机名差异

不要将：

```text
example.com
```

视为严格跨引擎等价规则。

跨三端应显式写为：

```text
||example.com^
```

uBO 对可识别为合法 hostname 的裸字符串按 HOSTS 风格处理，而 ABP 将其视为普通 URL pattern。

---

## 3. 请求资源类型

| 语义 | AdGuard | uBO | ABP | 等级 |
|---|---|---|---|---|
| JavaScript | `$script` | `$script` | `$script` | A |
| 图片 | `$image` | `$image` | `$image` | A |
| 样式表 | `$stylesheet` | `$stylesheet` | `$stylesheet` | A |
| 字体 | `$font` | `$font` | `$font` | A |
| 音视频 | `$media` | `$media` | `$media` | A |
| Object / plugin | `$object` | `$object` | `$object` | A |
| XHR / Fetch | `$xmlhttprequest` | `$xmlhttprequest` | `$xmlhttprequest` | A |
| XHR 简写 | `$xhr` | `$xhr` | — | B（AdGuard ↔ uBO） |
| 子文档 | `$subdocument` | `$subdocument` | `$subdocument` | A |
| 主文档 | `$document` | `$document` | `$document` | △ |
| Ping / Beacon | `$ping` | `$ping` | `$ping` | A |
| WebSocket | `$websocket` | `$websocket` | `$websocket` | A |
| Popup | `$popup` | `$popup` | `$popup` | △ |
| 其他请求 | `$other` | `$other` | `$other` | A |

**公共规范建议：**

```text
$xmlhttprequest
```

不要以：

```text
$xhr
```

作为跨引擎公共语法。

---

## 4. 请求上下文与作用域

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 第三方请求 | `$third-party` | `$third-party` | `$third-party` | A | 三端公共。 |
| 第一方请求 | `$~third-party` | `$~third-party` | `$~third-party` | A | 最稳定的公共写法。 |
| 第一方别名 | `$first-party` | `$first-party` / `$1p` | 使用 `$~third-party` | C | 不应作为统一源码。 |
| 第三方简写 | — | `$3p` | — | D/uBO | uBO 简写。 |
| 来源域限制 | `$domain=` | `$domain=` | `$domain=` | A | 限制规则生效页面/来源上下文。 |
| 排除来源域 | `$domain=~foo.com` | 同 | 同 | A | 三端公共。 |
| 多来源域 | `$domain=a.com\|b.com` | 同 | 同 | A | 三端公共。 |
| 目标域限制 | `$to=` | `$to=` | — | B | AdGuard ↔ uBO。 |
| 目标域排除 | `$denyallow=` | `$denyallow=` | — | B | AdGuard ↔ uBO。 |
| HTTP 方法 | `$method=` | `$method=` | — | B | AdGuard ↔ uBO。 |
| 应用程序作用域 | `$app=` | — | — | D/AG | AdGuard 可按 Android 包名、进程名或应用标识限定。 |

---

## 5. 规则优先级与禁用

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 高优先级阻止 | `$important` | `$important` | — | B | AdGuard ↔ uBO。 |
| 禁用已有规则 | `$badfilter` | `$badfilter` | — | B | AdGuard ↔ uBO。 |
| 普通网络例外 | `@@` | `@@` | `@@` | A | 三端公共。 |

---

## 6. URL 查询参数与 URL 转换

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 删除指定查询参数 | `$removeparam=foo` | `$removeparam=foo` | — | B | AdGuard ↔ uBO。 |
| 正则删除查询参数 | `$removeparam=/regexp/` | `$removeparam=/regexp/` | — | B | AdGuard ↔ uBO。 |
| 删除全部查询参数 | `$removeparam` | `$removeparam` | — | B | AdGuard ↔ uBO。 |
| 历史别名 | `$queryprune=` | `$queryprune=` | — | △ | 新规则应统一使用 `$removeparam=`。 |
| URL 转换 | `$urltransform=` | `$uritransform=` | — | C | 功能对应，语法不同。 |
| URL 解包 / 跳转 | — | `$urlskip=` | — | D/uBO | uBO 专有。 |

---

## 7. 网络重定向

### 7.1 AdGuard / uBO

两者使用同类语法：

```text
$redirect=RESOURCE
```

例如：

```text
||example.com/ad.js$script,redirect=noopjs
```

AdGuard 官方明确说明其 `$redirect` 使用与 uBlock Origin 相同的过滤规则语法。

### 7.2 Adblock Plus

ABP 使用：

```text
$rewrite=abp-resource:RESOURCE
```

例如：

```text
$rewrite=abp-resource:blank-js
$rewrite=abp-resource:blank-text
```

### 7.3 对照

| 功能 | AdGuard | uBO | ABP | 等级 |
|---|---|---|---|---|
| 内部资源重定向 | `$redirect=` | `$redirect=` | `$rewrite=abp-resource:` | B / C |
| 空 JavaScript | redirect resource token | redirect resource token | `abp-resource:blank-js` | C |
| 空文本 | redirect resource token | redirect resource token | `abp-resource:blank-text` | C |
| 独立重定向指令 | `$redirect-rule=` | `$redirect-rule=` | — | B/△ |
| ABP rewrite 语法 | 兼容 | — | 原生 | B AG↔ABP |

**注意：** 内部资源 token 名称不是跨引擎标准。即使功能等价，也不能据此判定原规则可原样复用。

---

## 8. 响应正文修改

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 通用文本响应正则替换 | `$replace=/pattern/replacement/` | `$replace=/pattern/replacement/` | — | B AG↔uBO | uBO 官方将 `$replace` 的语义指向 AdGuard 规范；同时具有 trusted-source 约束。 |
| XHR 响应替换 | `$replace=` / Scriptlet | `$replace=` / Scriptlet | `replace-xhr-response` Snippet | C | ABP 从 Snippet 层实现。 |
| Fetch 响应替换 | `$replace=` / Scriptlet | `$replace=` / Scriptlet | `replace-fetch-response` Snippet | C | 语法和执行层不同。 |
| XHR 请求体修改 | Scriptlet / 其他机制 | Scriptlet / 其他机制 | `replace-xhr-request` Snippet | C | ABP 修改 outgoing XHR body。 |
| Fetch 请求体修改 | Scriptlet / 其他机制 | Scriptlet / 其他机制 | `replace-fetch-request` Snippet | C | ABP 修改 outgoing fetch body。 |

---

## 9. CSP 与 Header

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| CSP 注入 | `$csp=` | `$csp=` | `$csp=` | A/△ | 规则语言层直接对应；合并、例外等细节需分别验证。 |
| Header 条件匹配 | `$header=` | `$header=` / 对应机制 | `$header=` | △ | 同名不代表参数语义完全一致。 |
| 删除响应 Header | `$removeheader=` | `##^responseheader(...)` 等 | — | C | 语法和执行机制不同。 |
| 添加 Header | Header 相关机制 | — / 不同机制 | `$addheader=` | C | ABP 独立 modifier。 |

---

## 10. 元素隐藏

| 功能 | AdGuard | uBO | ABP | 等级 |
|---|---|---|---|---|
| 基础元素隐藏 | `##selector` | `##selector` | `##selector` | A |
| 限定域元素隐藏 | `example.com##selector` | 同 | 同 | A |
| 多域元素隐藏 | `a.com,b.com##selector` | 同 | 同 | A |
| 通配顶级域限定 | `example.*##selector` | 同 | 同 | △ |
| 元素隐藏例外 | `#@#selector` | `#@#selector` | `#@#selector` | A |

这是除基础网络匹配之外最稳定的三端公共语法层。三者当前都支持元素规则域名部分的 `example.*` 写法，但各实现的版本边界和更复杂通配模式并不完全相同，因此转换器只能确认并透传该通配顶级域形式，不能把任意 `*` 域名模式一概视为同构语法。

---

## 11. Extended / Procedural Cosmetic Filtering

| 目标 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 标准 CSS `:has()` | 支持 | 支持 | 支持 | A/△ | 优先使用标准 CSS 语义。 |
| 文本匹配 | ExtendedCSS | `:has-text()` 等 | Extended selector / Snippet | C | 语法体系不同。 |
| 计算样式匹配 | `:matches-css()` 等 | `:matches-css()` | Extended selector 对应能力 | C/△ | 逐条验证。 |
| XPath | `:xpath()` | `:xpath()` | 无统一同构形式 | B/C | AdGuard/uBO 较接近。 |
| DOM 删除 | `:remove()` / ExtendedCSS | `:remove()` | `{remove:true}` 等 | C | 功能对应，写法不同。 |
| CSS 注入 | `#$#...` 等 | `:style(...)` / 兼容部分 AG 语法 | ABP content-filter CSS | C | 不属于三端公共语法。 |

---

## 12. Scriptlet / Snippet

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 预定义 JavaScript 干预 | Scriptlet | Scriptlet | Snippet | C | 功能层对应，不是统一规则语言。 |
| 原生调用形式 | `#%#//scriptlet(...)` 等 | `##+js(name,args)` | `#$#snippet-name ...` | C | 调用协议不同。 |
| uBO Scriptlet 输入 | 兼容大量 uBO 语法 | 原生 | — | 单向/△ | 兼容输入不等于脚本库完全一致。 |
| ABP Snippet 输入 | 支持部分兼容能力 | — | 原生 | 单向/△ | 必须按具体 snippet 名称验证。 |

---

## 13. HTML Filtering

| 功能 | AdGuard | uBO | ABP | 等级 | 说明 |
|---|---|---|---|---|---|
| 解析前 HTML Filtering | `$$` 体系 | `##^` 体系 | — | C AG↔uBO | 功能对应，不是语法兼容。 |
| DOM 加载后处理 | Cosmetic / ExtendedCSS | Procedural cosmetic | Extended CSS / Snippet | C | 执行层不同。 |

示例：

```text
AdGuard:
example.com$$script[src*="ads"]

uBO:
example.com##^script[src*="ads"]
```

两者是功能对应，不是原样兼容。

---

## 14. 网络例外范围

| 功能 | AdGuard | uBO | ABP | 等级 |
|---|---|---|---|---|
| 页面级例外 | `@@...$document` | `@@...$document` | `@@...$document` | A/△ |
| 禁用元素隐藏 | `$elemhide` | `$elemhide` | `$elemhide` | A |
| 禁用通用元素隐藏 | `$generichide` | `$generichide` | `$generichide` | A |
| 禁用通用网络过滤 | `$genericblock` | — | `$genericblock` | B（AdGuard ↔ ABP）/ D（uBO） |
| 禁用特定元素隐藏 | `$specifichide` | `$specifichide` | — | B AG↔uBO |

---

## 15. 引擎专有 / 主要扩展能力

### 15.1 AdGuard

| 语法 | 用途 | 对应关系 |
|---|---|---|
| `$app=` | 按应用/进程限定过滤作用域 | D |
| `$cookie=` | Cookie 级网络处理 | D/C |
| `$hls=` | HLS playlist 处理 | D/C |
| `$jsonprune=` | JSON 响应裁剪 | C |
| `$xmlprune=` | XML 响应裁剪 | C |
| `$referrerpolicy=` | Referrer Policy 处理 | C |
| `$permissions=` | Permissions Policy 处理 | C |

### 15.2 uBlock Origin

| 语法 | 用途 | 对应关系 |
|---|---|---|
| `$1p` / `$3p` | 第一方/第三方简写 | C |
| `$urlskip=` | URL 解包 / 跳转处理 | D |
| `$ipaddress=` | 按目标 IP 匹配 | D |
| `$uritransform=` | URL 转换 | C |
| `##^responseheader(...)` | 响应 Header 处理 | C |
| Procedural cosmetic filters | 高级 DOM / selector 处理 | C |

### 15.3 Adblock Plus

| 语法 / 体系 | 用途 | 对应关系 |
|---|---|---|
| `$rewrite=abp-resource:*` | 内部资源重写 | B/C |
| `$sitekey=` | 站点密钥限定 | D |
| `$addheader=` | Header 注入 | C |
| `#$#...` Snippet | 预定义 JavaScript 干预 | C |
| `replace-xhr-response` | XHR 响应替换 | C |
| `replace-fetch-response` | Fetch 响应替换 | C |

---

## 16. 推荐的多引擎规则分层

| 层级 | 适用范围 | 代表语法 | 维护原则 |
|---|---|---|---|
| **Tier 1 — 三端公共** | AdGuard + uBO + ABP | `||` `^` `*` `@@` `$domain` `$third-party` `$xmlhttprequest` `##` `#@#` 等 | 统一源码。 |
| **Tier 2 — AdGuard/uBO 公共扩展** | AdGuard + uBO | `$important` `$badfilter` `$to` `$denyallow` `$method` `$removeparam` `$redirect` `$replace` | 单独分支，不直接下发 ABP。 |
| **Tier 3 — 功能映射** | 三端都能实现，但语法不同 | Scriptlet/Snippet、响应改写、Header、HTML filtering、Extended selectors | 按目标引擎分别生成。 |
| **Tier 4 — 引擎专有** | 单引擎 | AG `$app`；uBO `$urlskip`；ABP `$sitekey` 等 | 保留为目标引擎专用规则。 |
| **Tier 5 — 单向兼容输入** | 某引擎主动兼容另一套语法 | 例如 AdGuard 对 ABP `$rewrite`、大量 uBO scriptlet 语法的兼容 | 不视为双向兼容。 |

---

## 17. 典型规则判定

| 规则 | AdGuard | uBO | ABP | 严格结论 |
|---|---:|---:|---:|---|
| `||alxanosoft.com/rk/$redirect=nooptext` | 支持* | 支持* | 不支持原语法 | AdGuard/uBO 属同类 `$redirect` 体系；resource token 必须分别存在。 |
| `||mdialog.com/*/stream_time_events$replace=/[\s\S]+/{}/` | 支持 | 支持* | 不支持原语法 | AdGuard/uBO 属 `$replace` 静态网络语法；ABP 需 Snippet。 |
| `...$xmlhttprequest,script,other,replace=...,app=fr.tf1.mytf1` | 支持 | 不支持 | 不支持 | `$app=` 使整体成为 AdGuard 专用作用域规则。 |
| `||facemap.foldlife.net^$app=com.estrongs.android.pop` | 支持 | 不支持 | 不支持 | uBO/ABP 无 Android App package 作用域等价语法。 |

\* 表示规则语言层支持，但仍需满足该引擎自身的资源 token、trusted-source 等规则约束。

---

## 18. 兼容关系摘要

```text
                     EasyList / ABP 基础语法
                  ┌──────────────────────────┐
                  │ ||  ^  *  |  @@          │
                  │ $domain                  │
                  │ $third-party             │
                  │ $script / $image / ...   │
                  │ $xmlhttprequest           │
                  │ ## / #@#                 │
                  └────────────┬─────────────┘
                               │
                    三端共同基础语法层
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
      AdGuard                 uBO                  ABP
          │                    │                    │
          ├──── 高度重叠 ──────┤                    │
          │ $important         │                    │
          │ $badfilter         │                    │
          │ $to                │                    │
          │ $denyallow         │                    │
          │ $removeparam       │                    │
          │ $redirect          │                    │
          │ $replace           │                    │
          │                    │                    │
          │◄──── 兼容 ABP $rewrite ────────────────┤
          │                                         │
          │                                         ├─ Snippet
          │                                         ├─ $rewrite
          │                                         ├─ $sitekey
          │                                         └─ $addheader
          │
          ├─ $app
          ├─ $cookie
          ├─ $hls
          ├─ $jsonprune
          └─ $xmlprune

                               uBO 专有/主要扩展：
                               $urlskip
                               $ipaddress
                               procedural filters
```

---

## 19. 维护建议

1. 三端共用规则仅使用 Tier 1。
2. AdGuard/uBO 高级网络规则维护独立公共分支。
3. Scriptlet/Snippet、HTML filtering、Header、响应改写按引擎分别生成。
4. 不把“单向兼容输入”标记为“双向语法兼容”。
5. 对 `$redirect` resource token、Scriptlet/Snippet 名称等依赖内置资源库的语法逐项核验。

---

## 20. 官方规范来源

- AdGuard — How to create your own ad filters  
  https://adguard.com/kb/general/ad-filtering/create-own-filters/

- uBlock Origin — Static filter syntax  
  https://github.com/gorhill/uBlock/wiki/Static-filter-syntax

- Adblock Plus — How to write filters  
  https://help.adblockplus.org/adblock-plus-help-center/how-to-write-filters

- Adblock Plus 4.1 — wildcard domains in element hiding filters  
  https://blog.adblockplus.org/releases/adblock-plus-41-for-chrome-firefox-microsoft-edge-and-opera

- Adblock Plus — Snippet filters tutorial  
  https://help.adblockplus.org/adblock-plus-help-center/snippet-filters-tutorial
