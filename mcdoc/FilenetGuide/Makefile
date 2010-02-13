# $Id$

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

include ../Makefile.global

GUIDE_NAME = FilenetGuide.pdf

TEX_FILES = $(wildcard *.tex)

all : $(GUIDE_NAME)

export TEXINPUTS = .:../MasterTemplate:./Images:../UserGuide:../ConnectorGuideInput::
$(GUIDE_NAME) : build.tex $(TEX_FILES)
	pdflatex $<
	pdflatex $<
	pdflatex $<
	mv build.pdf $@

install : $(GUIDE_NAME)
	install -o root -g root -d $(DESTDIR)/usr/share/doc/metacarta
	install -v -o root -g root -m644 $< $(DESTDIR)/usr/share/doc/metacarta

clean :
	rm -f $(GUIDE_NAME) *.log *.out *.aux
