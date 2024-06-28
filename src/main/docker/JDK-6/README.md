# Building Apache ManifoldCF 1.2 Docker Image with Oracle JDK 1.6

In order to build ManifoldCF from version 1.2 to 1.10 you have to:

1. Download jre-6u45-linux-x64.bin from the Oracle Portal and save it in this folder
2. Download or build ManifoldCF 1.2 and be sure to have the dist folder in the same folder of this Dockerfile
3. Build the Docker image with the following command:
 
 For version 1.2:
 
 `docker build --build-arg="MCF_VERSION=1.2" --progress=plain --platform linux/amd64 . -t apache/manifoldcf:1.2`
 
4. Run ManifoldCF 1.2 with:

`docker run -p 8345:8345 apache/manifoldcf:1.2`

