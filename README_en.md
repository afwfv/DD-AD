<div align="center">
<h1>AdGuard Rule</h1>
  <p>
    A simple Java program，Used to merge and update AdGuard filter rules
  </p>

<!-- Badges -->
<p>
  <a href="https://github.com/fordes123/adg-rule">
    <img src="https://img.shields.io/github/last-commit/fordes123/adg-rule?style=flat-square" alt="last update" />
  </a>
  <a href="https://github.com/fordes123/adg-rule">
    <img src="https://img.shields.io/github/forks/fordes123/adg-rule?style=flat-square" alt="forks" />
  </a>
  <a href="https://github.com/fordes123/adg-rule">
    <img src="https://img.shields.io/github/stars/fordes123/adg-rule?style=flat-square" alt="stars" />
  </a>
  <a href="https://github.com/fordes123/adg-rule/issues/">
    <img src="https://img.shields.io/github/issues/fordes123/adg-rule?style=flat-square" alt="open issues" />
  </a>
  <a href="https://github.com/fordes123/adg-rule">
    <img src="https://img.shields.io/github/license/fordes123/adg-rule?style=flat-square" alt="license" />
  </a>
</p>

<h4>
    <a href="#a">Introduction</a>
  <span> · </span>
    <a href="#b">Subscription</a>
  <span> · </span>
    <a href="#c">Quick start</a>
  <span> · </span>
    <a href="#d">Discussion</a>
  </h4>
</div>

<p align="center">
    <a href="/README_en.md">English </a>
    ·
    <a href="https://github.com/fordes123/adg-rule">简体中文</a>
</p>
<br />

<h2 id="a">📔 Introduction</h2>

This project aims to integrate `AdGuard` rules on demand。Regularly obtain rules from upstream subscriptions，Remove
duplicate and unsupported rules and categorize them.

#### upstream rules

<details>
<summary>Show</summary>
<ul>
    <li><a href="https://github.com/hoshsadiq/adblock-nocoin-list/">adblock-nocoin-list</a></li>
    <li><a href="https://github.com/durablenapkin/scamblocklist">Scam Blocklist</a></li>
    <li><a href="https://someonewhocares.org/hosts/zero/hosts">Dan Pollock's List</a></li>
    <li><a href="https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_15_DnsFilter/filter.txt">AdGuard DNS filter</a></li>
    <li><a href="https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=1&mimetype=plaintext">Peter Lowe's List</a></li>
    <li><a href="https://abp.oisd.nl/basic/">OISD Blocklist Basic</a></li>
    <li><a href="https://adaway.org/hosts.txt">AdAway Default Blocklist</a></li>
    <li><a href="https://github.com/crazy-max/WindowsSpyBlocker">WindowsSpyBlocker</a></li>
    <li><a href="https://github.com/o0HalfLife0o/list">HalfLife（pc）</a></li>
    <li><a href="https://github.com/banbendalao/ADgk">Adgk</a></li>
    <li><a href="https://github.com/VeleSila/yhosts">yhosts</a></li>
    <li><a href="https://github.com/jdlingyu/ad-wars">ad-wars</a></li> 
    <li><a href="https://gitlab.com/quidsup/notrack-blocklists">NoTrack Tracker Blocklist</a></li> 
    <li><a href="https://gitlab.com/cats-team/adrules/">AdRules(AdGuard Full List)</a></li>
    <li><a href="https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_2_Base/filter.txt">AdGuard Base</a></li>
</ul>
</details>

#### local rules

- [mylist](#)

> It is mainly a correction and supplement to the upstream rules. According to the daily use experience, some mistakes
> are blocked.

<h2 id="b">🎯 Subscription</h2>

| name         | desc                                                                                                      | github link         | jsDelivr link (delay)                                                    |
|--------------|-----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `all.txt`    | A collection of deduplication rules, including all the following rules, applicable to `AdGuard` clients | [✈️view](https://raw.githubusercontent.com/fordes123/adg-rule/main/rule/all.txt)    | [🚀view](https://cdn.jsdelivr.net/gh/fordes123/adg-rule/rule/all.txt)    |
| `adgh.txt`   | Rules for `AdGuardHome` , including `hosts.txt` and `mylist.txt`                                          | [✈️view](https://raw.githubusercontent.com/fordes123/adg-rule/main/rule/adgh.txt)   | [🚀view](https://cdn.jsdelivr.net/gh/fordes123/adg-rule/rule/adgh.txt)   |
| `hosts.txt`  | `hosts` rules                                                                                             | [✈️view](https://raw.githubusercontent.com/fordes123/adg-rule/main/rule/hosts.txt)  | [🚀view](https://cdn.jsdelivr.net/gh/fordes123/adg-rule/rule/hosts.txt)  |
| `mylist.txt` | Supplementary rules for self-use, manual update                                                           | [✈️view](https://raw.githubusercontent.com/fordes123/adg-rule/main/rule/mylist.txt) | [🚀view](https://cdn.jsdelivr.net/gh/fordes123/adg-rule/rule/mylist.txt) |

<h2 id="c">🛠️ Quick start</h2>

### Example configuration

```yaml
application:
  rule:       
    #Remote rule subscription, only supports http, https
    remote:
      - 'https://example.com/list.txt'
    #Local rules, please move the file to the project path rule directory
    local: 
      - 'mylist.txt'
  output:
    path: rule   #The output path of the rule file, the relative path starts from the project directory by default
    files:
      all.txt:    #output filename
        - DOMAIN  #omain name rules, full domain names only
        - REGEX   #Regular rules, including regular domain name rules, AdGH support
        - MODIFY  #Modification rules, add some rules for modification symbols, AdG support
        - HOSTS   #Hosts rules
```

#### local run

```bash
git clone https://github.com/fordes123/adg-rule.git
cd adg-rule
mvn clean
mvn spring-boot:run
```

#### use github action

- fork this project.
- Referring to the example configuration, modify the configuration file: `src/main/resources/application.yml`, note that the local rule file should be placed in the project root directory `rule` folder.
- Edit the `.github/workflows/auto-update.yml` file and change the email and username under the `Commit Changes` block to your own (Github email and username).
- Commit all changes and wait for `Github Action` to execute. After execution, the corresponding rules are generated in the directory specified in the configuration.

<h2 id="d">💬 Discussion</h2>

- 👉 [issues](https://github.com/fordes123/adg-rule/issues)
