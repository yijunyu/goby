FROM gitpod/workspace-full
RUN wget https://github.com/protocolbuffers/protobuf/releases/download/v3.8.0/protobuf-cpp-3.8.0.tar.gz \
  & tar xvfz protobuf-cpp-3.8.0.tar.gz \
  & cd protobuf-3.8.0 \
  & ./configure \
  & make -j16 \
  & make install \
  & cd .. \
  & rm -rf protobuf-3.8.0 \
  & rm -f protobuf-cpp-3.8.0.tar.gz
