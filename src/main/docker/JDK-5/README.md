# Building Apache ManifoldCF 1.x Docker Image with Oracle JDK 1.5

In order to build ManifoldCF from version 1.0 to 1.1 you have to:

1. Download jre-1_5_0_22-linux-amd64.bin from the Oracle Portal and save it in this folder
2. Download or build ManifoldCF 1.x and be sure to have the dist folder in the same folder of this Dockerfile
3. Build the Docker image with the following command:
 
 For version 1.0:
 
 `docker build --build-arg="MCF_VERSION=1.0" --progress=plain --platform linux/amd64 . -t apache/manifoldcf:1.0`
 
 For version 1.1:
 
 `docker build --build-arg="MCF_VERSION=1.1" --progress=plain --platform linux/amd64 . -t apache/manifoldcf:1.1`
 
4. Run ManifoldCF 1.1 with:

`docker run -p 8345:8345 apache/manifoldcf:1.1`