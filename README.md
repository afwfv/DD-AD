# DD-AD

## 社区与联系

- TG 频道: https://t.me/DDadsss
- QQ 群: http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=z4tq1QhIHdGOX6PslCFBqDRBqH6WGfXb&authKey=Inrcu9LZL6G6%2F26qpdxo9WEAw0nQuJ%2FpIqGuKsrX1kOgSVSZRQkyLxqfvKoJDlEB&noverify=0&group_code=666178576

## 🎯 规则订阅建议

- AdGuard 客户端（软件 / 浏览器扩展）、AdBlock、AdBlockPlus、uBlock Origin 推荐使用 `easylist.txt`。
- AdGuard Home 推荐使用 `dns.txt`。
- 仅支持 hosts 的工具（如 AdAway）请使用 `hosts`。
| 文件 | 说明 | Github raw | ghproxy | jsdelivr |
|---|---:|:---:|:---:|:---:|
| `easylist.txt` | 完整主规则 | [link][easylist-raw] | [link][easylist-ghproxy] | [link][easylist-jsdelivr] |
| `modify.txt` | 不含 DNS 过滤规则的 `easylist.txt` | [link][modify-raw] | [link][modify-ghproxy] | [link][modify-jsdelivr] |
| `dns.txt` | 仅含 DNS 过滤规则的 `easylist.txt` | [link][dns-raw] | [link][dns-ghproxy] | [link][dns-jsdelivr] |
| `dnsmasq.conf` | dnsmasq 及其衍生版本 | [link][dnsmasq-raw] | [link][dnsmasq-ghproxy] | [link][dnsmasq-jsdelivr] |
| `clash.yaml` | clash 及其衍生版本 | [link][clash-raw] | [link][clash-ghproxy] | [link][clash-jsdelivr] |
| `smartdns.conf` | smartdns | [link][smartdns-raw] | [link][smartdns-ghproxy] | [link][smartdns-jsdelivr] |
| `hosts` | 操作系统原生支持的 hosts 文件 | [link][hosts-raw] | [link][hosts-ghproxy] | [link][hosts-jsdelivr] |
| `DD-AD.txt` | 本仓库维护的私有规则（easylist 格式） | [link][DD-AD-raw] | [link][DD-AD-ghproxy] | [link][DD-AD-jsdelivr] |

[easylist-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/easylist.txt
[easylist-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/easylist.txt
[easylist-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/easylist.txt
[modify-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/modify.txt
[modify-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/modify.txt
[modify-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/modify.txt
[dns-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/dns.txt
[dns-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/dns.txt
[dns-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/dns.txt
[dnsmasq-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/dnsmasq.conf
[dnsmasq-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/dnsmasq.conf
[dnsmasq-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/dnsmasq.conf
[clash-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/clash.yaml
[clash-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/clash.yaml
[clash-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/clash.yaml
[smartdns-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/smartdns.conf
[smartdns-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/smartdns.conf
[smartdns-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/smartdns.conf
[hosts-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/hosts
[hosts-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/hosts
[hosts-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/hosts
[DD-AD-raw]: https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/DD-AD.txt
[DD-AD-ghproxy]: https://ghproxy.net/https://raw.githubusercontent.com/afwfv/DD-AD/refs/heads/release/DD-AD.txt
[DD-AD-jsdelivr]: https://gcore.jsdelivr.net/gh/afwfv/DD-AD@refs/heads/release/DD-AD.txt

### 说明

本仓库整合常见广告过滤规则，使用项目：[ad-filters-subscriber](https://github.com/fordes123/ad-filters-subscriber)

- 已为部分应用（例如番茄小说、七猫小说）添加针对性的规则。
- 私人 DNS: dd.afwfv.cn
- 规则每日更新，更新时间约为 UTC+8 每日 00:30 左右。