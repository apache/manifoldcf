#!/bin/sh
gpg_local_user=$1
mcf_version=$2

gpg --local-user "$1" --armor --output apache-manifoldcf-$2-src.zip.asc --detach-sig apache-manifoldcf-$2-src.zip
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-src.tar.gz.asc --detach-sig apache-manifoldcf-$2-src.tar.gz
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-lib.zip.asc --detach-sig apache-manifoldcf-$2-lib.zip
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-lib.tar.gz.asc --detach-sig apache-manifoldcf-$2-lib.tar.gz
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-bin.zip.asc --detach-sig apache-manifoldcf-$2-bin.zip
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-bin.tar.gz.asc --detach-sig apache-manifoldcf-$2-bin.tar.gz

gpg --print-md MD5 apache-manifoldcf-$2-src.zip > apache-manifoldcf-$2-src.zip.md5
gpg --print-md MD5 apache-manifoldcf-$2-src.tar.gz > apache-manifoldcf-$2-src.tar.gz.md5
gpg --print-md MD5 apache-manifoldcf-$2-lib.zip > apache-manifoldcf-$2-lib.zip.md5
gpg --print-md MD5 apache-manifoldcf-$2-lib.tar.gz > apache-manifoldcf-$2-lib.tar.gz.md5
gpg --print-md MD5 apache-manifoldcf-$2-bin.zip > apache-manifoldcf-$2-bin.zip.md5
gpg --print-md MD5 apache-manifoldcf-$2-bin.tar.gz > apache-manifoldcf-$2-bin.tar.gz.md5

gpg --print-md SHA512 apache-manifoldcf-$2-src.zip > apache-manifoldcf-$2-src.zip.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-src.tar.gz > apache-manifoldcf-$2-src.tar.gz.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-lib.zip > apache-manifoldcf-$2-lib.zip.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-lib.tar.gz > apache-manifoldcf-$2-lib.tar.gz.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-bin.zip > apache-manifoldcf-$2-bin.zip.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-bin.tar.gz > apache-manifoldcf-$2-bin.tar.gz.sha512