FROM gitpod/workspace-full
<<<<<<< HEAD
RUN wget https://github.com/protocolbuffers/protobuf/releases/download/v3.8.0/protobuf-cpp-3.8.0.tar.gz \
  & tar xvfz protobuf-cpp-3.8.0.tar.gz \
  & cd protobuf-cpp-3.8.0 \
  & ./configure --prefix=/usr \
  & make -j16 \
  & make install \
  & cd .. \
  & rm -rf protobuf-cpp-3.8.0 \
  & rm protobuf-cpp-3.8.0.tar.gz
=======

>>>>>>> fd17ac90b51ba4a5b33e095fcd72b9c0ef660575
