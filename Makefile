.PHONY: setup build check test coverage package clean

setup:            ## 构建 + 审计 + 演练 + 安装（zh_CN）
	bin/igv-l10n setup zh_CN

build:
	bin/igv-l10n build

check:
	bin/igv-l10n check

test:
	bin/igv-l10n test && tests/run.sh

coverage:
	bin/igv-l10n coverage zh_CN

package:
	bin/igv-l10n package zh_CN

clean:
	rm -rf build out dist
