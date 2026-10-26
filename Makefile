.PHONY: setup build check test coverage package clean

setup:            ## 构建 + 审计 + 演练 + 安装（zh_CN）
	bin/igv-lingo setup zh_CN

build:
	bin/igv-lingo build

check:
	bin/igv-lingo check

test:
	bin/igv-lingo test && tests/run.sh

coverage:
	bin/igv-lingo coverage zh_CN

package:
	bin/igv-lingo package zh_CN

clean:
	rm -rf build out dist
