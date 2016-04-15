# Makefile for Fishnet

JAVAC = javac
FLAGS = -nowarn -g
JAVA_FILES = $(wildcard lib/*.java) $(wildcard proj/*.java)

.PHONY = all clean

all: $(JAVA_FILES)
	@echo 'Making all...'
	@$(JAVAC) $(FLAGS) $?

clean:
	rm -f $(JAVA_FILES:.java=.class)
	rm -f *~ lib/*~ proj/*~

simpletest:
	perl fishnet.pl simulate 2 scripts/transfertest.fish

congestion:
	perl fishnet.pl simulate 7 scripts/congestion-control.fish

no-congestion:
	perl fishnet.pl simulate 7 scripts/no-congestion-control.fish

buffertest:
	perl fishnet.pl simulate 3 scripts/buffertest.fish

docs:
	rm -rf javadoc
	mkdir javadoc
	javadoc -d javadoc/ -classpath javadoc/ proj/*.java

