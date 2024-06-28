# Building Apache ManifoldCF 2.26 Docker Image with Open JDK 11

In order to build ManifoldCF from version 2.26 you have to:

1. Download or build ManifoldCF 2.26 and be sure to have the dist folder in the same folder of this Dockerfile
2. Build the Docker image with the following command:
 
 For version 2.26:
 
 `docker build --build-arg="MCF_VERSION=2.26" --progress=plain . -t apache/manifoldcf:2.26`
 
4. Run ManifoldCF 2.25 with:

`docker run -p 8345:8345 apache/manifoldcf:2.25`

