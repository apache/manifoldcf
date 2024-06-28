# Building Apache ManifoldCF 2.7-2.25 Docker Image with Open JDK 8

In order to build ManifoldCF from version 2.7 to 2.25 you have to:

1. Download or build ManifoldCF 2.7-2.25 and be sure to have the dist folder in the same folder of this Dockerfile
2. Build the Docker image with the following command:
 
 For version 2.25:
 
 `docker build --build-arg="MCF_VERSION=2.25" --progress=plain . -t apache/manifoldcf:2.25`
 
4. Run ManifoldCF 2.25 with:

`docker run -p 8345:8345 apache/manifoldcf:2.25`

