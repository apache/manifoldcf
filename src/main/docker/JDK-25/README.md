# Building Apache ManifoldCF 2.31 Docker Image with Open JDK 25

In order to build ManifoldCF from version 2.31 you have to:

1. Download or build ManifoldCF 2.31 and be sure to have the dist folder in the same folder of this Dockerfile
2. Build the Docker image with the following command:
 
 For version 2.31:
 
 `docker build --build-arg="MCF_VERSION=2.31" --progress=plain . -t apache/manifoldcf:2.31`
 
4. Run ManifoldCF 2.31 with:

`docker run -p 8345:8345 apache/manifoldcf:2.31`

