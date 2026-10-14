#!/usr/bin/env bash
# 从目录树推导平台矩阵：**平台/窗口 = 一个含构建脚本的目录**，事实写在它的 gradle.properties 里。
# 加一个窗口 = 建那个目录（+ settings.gradle.kts include 一行，构建拓扑保持显式），CI 零改动。
#
# 每个窗口必须声明 publish_game_versions（声称覆盖哪些游戏版本）；可选 publish_loaders
# 覆盖推导出来的 loader（默认就是平台名，Paper 额外加 Folia）。
#
# 输出到 $GITHUB_OUTPUT（同时打到 stdout）：version / matrix / jars。
# 本地跑：bash .github/scripts/platform-matrix.sh
#         INPUT_PLATFORMS=forge INPUT_VERSIONS=1.20.1 bash .github/scripts/platform-matrix.sh
set -euo pipefail

ROOT_PROPS="${ROOT_PROPS:-gradle.properties}"

# 取 `key=` 的值。`-m1` 防重复键，`cut -f2-` 保留值里的 `=`；找不到就返回空串（不触发 set -e）。
read_prop() {
  grep -m1 -E "^$1=" "$2" 2>/dev/null | cut -d '=' -f 2- || true
}

VERSION=$(read_prop mod_version "$ROOT_PROPS")
ARCHIVES=$(read_prop archives_base_name "$ROOT_PROPS")
if [ -z "$VERSION" ] || [ -z "$ARCHIVES" ]; then
  echo "::error::从 $ROOT_PROPS 读不到 mod_version 或 archives_base_name" >&2
  exit 1
fi

# 筛选：空格与大小写都不该让它失灵；`all` 与空串等价（手动入口的下拉框用 all，程序化调用传空串）。
WANT_PLATFORMS=$(printf '%s' "${INPUT_PLATFORMS:-}" | tr -d ' ' | tr 'A-Z' 'a-z')
WANT_VERSIONS=$(printf '%s' "${INPUT_VERSIONS:-}" | tr -d ' ' | tr 'A-Z' 'a-z')
[ "$WANT_PLATFORMS" = "all" ] && WANT_PLATFORMS=""
[ "$WANT_VERSIONS" = "all" ] && WANT_VERSIONS=""

# 这个窗口要不要。名字格式是「平台-窗口起点」，所以从**第一个** '-' 切开
# （用 #*- 而不是 ##*-：窗口名里若再有 '-'，按第一个切才对）。
want() {
  local name=$1 platform window matched v p
  platform=${name%%-*}
  window=${name#*-}
  if [ -n "$WANT_VERSIONS" ]; then
    matched=0
    for v in ${WANT_VERSIONS//,/ }; do [ "$v" = "$window" ] && matched=1; done
    [ "$matched" = 1 ] || return 1
  fi
  if [ -n "$WANT_PLATFORMS" ]; then
    matched=0
    for p in ${WANT_PLATFORMS//,/ }; do
      if [ "$p" = "$platform" ] || [ "$p" = "$name" ]; then matched=1; fi
    done
    [ "$matched" = 1 ] || return 1
  fi
  return 0
}

# 发布时用什么 loader。默认就是平台名；Paper 额外声明支持 Folia，所以单独一条规则。
# 需要别的写法就在那个窗口的 gradle.properties 里写 publish_loaders=... 覆盖。
loaders_for() {
  local platform=$1 override=$2
  if [ -n "$override" ]; then echo "$override"; return; fi
  case "$platform" in
    paper) echo "paper folia" ;;
    *) echo "$platform" ;;
  esac
}

rows=()
jars=()
# 已发出的「平台|窗口全名」对，用来回报"哪些筛选 token 一个都没匹配上"（拼错了要说一声）。
matched=()

# 扫两级：平台目录 → 窗口目录。一个窗口 = 一个**含构建脚本**的目录，
# 所以 run/ build/ 之类没有构建脚本的目录天然被跳过。
for platform_dir in */; do
  platform="${platform_dir%/}"
  [ -d "$platform_dir" ] || continue
  for window_dir in "$platform_dir"*/; do
    [ -d "$window_dir" ] || continue
    if [ ! -f "${window_dir}build.gradle" ] && [ ! -f "${window_dir}build.gradle.kts" ]; then
      continue
    fi
    # 目录名本身就是窗口全名（fabric-26.1）—— 平台名已经含在里面，不要再拼一次。
    name="${window_dir%/}"
    name="${name##*/}"
    want "$name" || continue
    matched+=("$platform|$name")

    props_file="${window_dir}gradle.properties"
    game_versions=$(read_prop publish_game_versions "$props_file")
    if [ -z "$game_versions" ]; then
      echo "::error::${props_file} 里没有 publish_game_versions —— 发布时不知道该声称哪些游戏版本" >&2
      exit 1
    fi
    loaders=$(loaders_for "$platform" "$(read_prop publish_loaders "$props_file")")

    # 产物路径用**通配**而不是写死版本号：release 会用 `-Pmod_version=<实际版本>` 构建，
    # 而非 main 分支上那个版本带 `-beta` 后缀（见 release.yml 的 gate），写死根 mod_version 就对不上。
    # 同目录里还有薄 jar（`-dev.jar`），由上传那一步的 `!**/*-dev.jar` 排除。
    jar="${window_dir}build/libs/${ARCHIVES}-${name}-*.jar"
    jars+=("$jar")
    rows+=("$(printf '{"name":"%s","project":":%s:%s","jar":"%s","loaders":"%s","game-versions":"%s"}' \
      "$name" "$platform" "$name" "$jar" "$loaders" "$game_versions")")
  done
done

# 筛选条件里"一个窗口都没匹配上"的 token —— **只警告，不阻断**：
# 拼错一个不该把其余匹配项也一起作废（"只找可找到的，找不到的就算了"）。
report_unmatched() {
  local kind=$1 tokens=$2 t pair p n hit
  [ -n "$tokens" ] || return 0
  for t in ${tokens//,/ }; do
    hit=0
    for pair in "${matched[@]}"; do
      p="${pair%%|*}"
      n="${pair#*|}"
      if [ "$t" = "$p" ] || [ "$t" = "$n" ]; then hit=1; fi
      if [ "$kind" = "versions" ] && [ "$t" = "${n#*-}" ]; then hit=1; fi
      if [ "$hit" = 1 ]; then break; fi
    done
    if [ "$hit" = 0 ]; then
      echo "::warning::${kind} 里的 '$t' 没匹配到任何窗口，已忽略"
    fi
  done
}
report_unmatched platforms "$WANT_PLATFORMS"
report_unmatched versions  "$WANT_VERSIONS"

# 一个都没匹配上就当场报错：那时矩阵为空 → 整个 job 被**静默跳过**，
# "跑成功了但什么都没发"比直接失败难查得多。
if [ ${#rows[@]} -eq 0 ]; then
  echo "::error::没有匹配的窗口 —— platforms='${WANT_PLATFORMS:-<全部>}' versions='${WANT_VERSIONS:-<全部>}'" >&2
  exit 1
fi

matrix=$(printf '{"include":[%s]}' "$(IFS=,; echo "${rows[*]}")")
jars_out=$(printf '%s\n' "${jars[@]}")

echo "version=$VERSION"
echo "选中 ${#rows[@]} 格"
echo "$matrix"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "version=$VERSION"
    echo "matrix=$matrix"
    echo "jars<<MATRIX_JARS_EOF"
    echo "$jars_out"
    echo "MATRIX_JARS_EOF"
  } >> "$GITHUB_OUTPUT"
fi
