@echo off

git clone https://github.com/CCExtractor/ccextractor.git
cd ccextractor
git apply ../change_filenames.patch
