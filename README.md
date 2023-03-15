<div align="center">
<h1>AD Filter Subscriber</h1>
  <p>
    一个简易的 Java 程序，用于整合不同来源的广告过滤规则，帮助你构建属于自己的规则集
  </p>
<!-- Badges -->
<p>
  <a href="https://github.com/fordes123/ad-filters-subscriber">
    <img src="https://img.shields.io/github/last-commit/fordes123/ad-filters-subscriber?style=flat-square" alt="last update" />
  </a>
  <a href="https://github.com/fordes123/ad-filters-subscriber">
    <img src="https://img.shields.io/github/forks/fordes123/ad-filters-subscriber?style=flat-square" alt="forks" />
  </a>
  <a href="https://github.com/fordes123/ad-filters-subscriber">
    <img src="https://img.shields.io/github/stars/fordes123/ad-filters-subscriber?style=flat-square" alt="stars" />
  </a>
  <a href="https://github.com/fordes123/ad-filters-subscriber/issues/">
    <img src="https://img.shields.io/github/issues/fordes123/ad-filters-subscriber?style=flat-square" alt="open issues" />
  </a>
  <a href="https://github.com/fordes123/ad-filters-subscriber">
    <img src="https://img.shields.io/github/license/fordes123/ad-filters-subscriber?style=flat-square" alt="license" />
  </a>
</p>

<h4>
    <a href="#a">项目说明</a>
  <span> · </span>
    <a href="#b">快速开始</a>
  <span> · </span>
    <a href="#c">规则订阅</a>
  <span> · </span>
    <a href="#d">问题反馈</a>
  </h4>
</div>

<br />

<h2 id="a">📔 项目说明</h2>

本项目旨在整合不同来源的广告过滤规则，通过 `Github Action` 定时执行，拉取远程规则，去重和分类输出。 根据过滤规则的特性，本项目将规则分为 `DOMAIN`、`REGEX`、`MODIFY`、`HOSTS` 四种类型，它们之间互不包含， 你可在配置文件中自由的对四种类型进行组合：

- `DOMAIN`：基于域名的过滤规则，适用于几乎所有广告过滤工具
- `REGEX`：基于正则表达式的**域名过滤**规则，适用于主流广告过滤工具
- `MODIFY`：基于正则和其他修饰符的过滤规则，可以拦截页面上的特定元素，但不适用于DNS过滤
- `HOSTS`：基于 `HOSTS` 的过滤规则，适用于支持 `HOSTS` 的所有设备

<br/>
<h2 id="b">🛠️ 快速开始</h2>

### 示例配置

```yaml
application:
  rule:
    #远程规则订阅，仅支持http、https
    remote:
      - 'https://example.com/list.txt'
    #本地规则，请将文件移动到项目路径rule目录中
    local:
      - 'mylist.txt'
  output:
    path: rule   #规则文件输出路径，相对路径默认从 项目目录开始
    files:
      all.txt: #输出文件名
        - DOMAIN
        - REGEX
        - MODIFY
        - HOSTS
```

### 直接运行

```bash
git clone https://github.com/fordes123/ad-filters-subscriber.git
cd ad-filters-subscriber
mvn clean
mvn spring-boot:run
```

### 使用 Github Action

- fork 本项目
- 自定义规则订阅 (可选)
    - 参照示例配置，修改配置文件: `src/main/resources/application.yml`，注意本地规则文件应放入项目根目录 `rule` 文件夹
- 打开 `Github Action` 页面，授权`Workflow`执行，点击 `Run workflow` 或等待自动执行。执行完成后相应规则生成在配置中指定的目录下

### 通过 `Codespaces` 运行

- 登录 `Github`，点击本仓库右上角 `Code` 按钮，选择并创建新的 `Codespaces`
- 等待 `Codespaces` 启动，即可直接对本项目进行调试

<br/>
<h2 id="c">🎯 规则订阅</h2>

| 名称           | 说明                                                                   |                                             Github                                             |                                         jsDelivr                                         |
|--------------|:---------------------------------------------------------------------|:----------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------:|
| `all.txt`    | 去重的规则合集，包含`DOMAIN`、`REGEX`、`MODIFY`、`HOSTS`，适用于 `AdGuard`、`AdBlock`等 |  [Link](https://raw.githubusercontent.com/fordes123/ad-filters-subscriber/main/rule/all.txt)   |  [Link](https://cdn.jsdelivr.net/gh/fordes123/ad-filters-subscriber@main/rule/all.txt)   |
| `dns.txt`    | DNS过滤规则集，包含`DOMAIN`、`REGEX`、`HOSTS`规则，适用于`AdGuardHome`等基于DNS的过滤工具    |  [Link](https://raw.githubusercontent.com/fordes123/ad-filters-subscriber/main/rule/dns.txt)   |  [Link](https://cdn.jsdelivr.net/gh/fordes123/ad-filters-subscriber@main/rule/dns.txt)   |
| `hosts.txt`  | 仅由`HOSTS`规则构成，适用于几乎所有设备                                              | [Link](https://raw.githubusercontent.com/fordes123/ad-filters-subscriber/main/rule/hosts.txt)  | [Link](https://cdn.jsdelivr.net/gh/fordes123/ad-filters-subscriber@main/rule/hosts.txt)  |
| `mylist.txt` | 本仓库维护的补充规则                                                           | [Link](https://raw.githubusercontent.com/fordes123/ad-filters-subscriber/main/rule/mylist.txt) | [Link](https://cdn.jsdelivr.net/gh/fordes123/ad-filters-subscriber@main/rule/mylist.txt) |

<details>
<summary>点击查看上游规则</summary>
<ul>
    <li><a href="https://github.com/hoshsadiq/adblock-nocoin-list/">adblock-nocoin-list</a></li>
    <li><a href="https://github.com/durablenapkin/scamblocklist">Scam Blocklist</a></li>
    <li><a href="https://someonewhocares.org/hosts/zero/hosts">Dan Pollock's List</a></li>
    <li><a href="https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_15_DnsFilter/filter.txt">AdGuard DNS filter</a></li>
    <li><a href="https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=1&mimetype=plaintext">Peter Lowe's List</a></li>
    <li><a href="https://abp.oisd.nl/basic/">OISD Blocklist Basic</a></li>
    <li><a href="https://adaway.org/hosts.txt">AdAway Default Blocklist</a></li>
    <li><a href="https://github.com/crazy-max/WindowsSpyBlocker">WindowsSpyBlocker</a></li>
    <li><a href="https://github.com/VeleSila/yhosts">yhosts</a></li>
    <li><a href="https://github.com/jdlingyu/ad-wars">ad-wars</a></li>
    <li><a href="https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_2_Base/filter.txt">AdGuard Base</a></li>
    <li><a href="https://github.com/TG-Twilight/AWAvenue-Adblock-Rule">AWAvenue-Adblock-Rule</a></li>
    <li><a href="https://github.com/neoFelhz/neohosts">neohosts</a></li>
    <li><a href="https://github.com/reek/anti-adblock-killer">anti-adblock-killer</a></li>
</ul>
</details>

> 我们更推荐 fork 本项目自行构建规则集，但如你准备本仓库提供的规则订阅，须知本仓库仅对规则进行整合，不对第三方规则进行维护，除`mylist.txt`外的任何错误请反馈到规则来源

<h2 id="d">💬 问题反馈</h2>

- 👉 [issues](https://github.com/fordes123/ad-filters-subscriber/issues)
