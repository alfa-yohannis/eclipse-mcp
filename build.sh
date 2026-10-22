#!/usr/bin/env bash
# Builds the Eclipse plug-ins of this repository against an Eclipse installation.
#
#   ./build.sh <eclipse-dir>             # bundle jars in build/
#   ./build.sh <eclipse-dir> --install   # also copies them into <eclipse-dir>/dropins
#
# The bundles compile with javac --release 11 against the jars in <eclipse-dir>/plugins.
# The Graphiti bundle is skipped when that installation has no Graphiti.

set -euo pipefail

usage() {
	sed -n '2,8p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
	exit 2
}

[[ $# -ge 1 && -d "$1/plugins" ]] || usage
ECLIPSE="$(cd "$1" && pwd)"
INSTALL=false
[[ "${2:-}" == "--install" ]] && INSTALL=true

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="$ROOT/build"
QUALIFIER="$(date -u +%Y%m%d%H%M)"
CORE=io.github.alfayohannis.eclipse.mcp
GRAPHITI=io.github.alfayohannis.eclipse.mcp.graphiti

# Clear only this build's outputs: a running Eclipse may hold an old jar open, and
# FUSE file systems keep such files as .fuse_hidden*, so the directory itself stays.
mkdir -p "$BUILD"
rm -rf "${BUILD:?}/$CORE" "${BUILD:?}/$GRAPHITI"
rm -f "$BUILD/${CORE}_"*.jar "$BUILD/${GRAPHITI}_"*.jar
# Plug-in jars, plus the jars inside directory-shaped plug-ins.
ECLIPSE_CLASSPATH="$({
	find "$ECLIPSE/plugins" -maxdepth 1 -name '*.jar'
	find "$ECLIPSE/plugins" -mindepth 2 -maxdepth 3 -name '*.jar'
} | sort | paste -sd:)"

build_bundle() {
	local name=$1 extra=$2
	local project="$ROOT/$name" work="$BUILD/$name"
	mkdir -p "$work/classes"
	find "$project/src" -name '*.java' >"$work/sources.txt"
	printf -- '-cp\n%s\n' "$extra$ECLIPSE_CLASSPATH" >"$work/javac.args"
	javac --release 11 -encoding UTF-8 -proc:none -Xlint:all,-path -d "$work/classes" \
		@"$work/javac.args" @"$work/sources.txt"
	cp "$project/plugin.xml" "$work/classes/"
	local version
	version="$(sed -n 's/^Bundle-Version: *//p' "$project/META-INF/MANIFEST.MF" | tr -d '\r' |
		sed "s/\.qualifier$/.$QUALIFIER/")"
	sed "s/^Bundle-Version: .*/Bundle-Version: $version/" "$project/META-INF/MANIFEST.MF" >"$work/MANIFEST.MF"
	jar --create --file "$BUILD/${name}_$version.jar" --manifest "$work/MANIFEST.MF" -C "$work/classes" .
	echo "Built build/${name}_$version.jar"
}

build_bundle "$CORE" ""
if compgen -G "$ECLIPSE/plugins/org.eclipse.graphiti.ui_*" >/dev/null; then
	build_bundle "$GRAPHITI" "$BUILD/$CORE/classes:"
else
	echo "Skipped $GRAPHITI: $ECLIPSE has no Graphiti"
fi

if $INSTALL; then
	mkdir -p "$ECLIPSE/dropins"
	rm -f "$ECLIPSE/dropins/${CORE}_"*.jar "$ECLIPSE/dropins/${GRAPHITI}_"*.jar
	cp "$BUILD"/*.jar "$ECLIPSE/dropins/"
	echo "Installed into $ECLIPSE/dropins; restart Eclipse to load the plug-ins"
fi
