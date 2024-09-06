# Building Apache ManifoldCF 1.3-2.6 Docker Image with Open JDK 7

In order to build ManifoldCF from version 1.3 to 2.6 you have to:

1. Download or build ManifoldCF 1.3-2.6 and be sure to have the dist folder in the same folder of this Dockerfile
2. Build the Docker image with the following command:
 
 For version 2.6:
 
 `docker build --build-arg="MCF_VERSION=2.6" --progress=plain --platform linux/amd64 . -t apache/manifoldcf:2.6`
 
4. Run ManifoldCF 2.6 with:

`docker run -p 8345:8345 apache/manifoldcf:2.6`

