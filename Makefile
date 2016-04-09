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

servertest:
	perl fishnet.pl simulate 2 scripts/servertest.fish

clienttest:
	perl fishnet.pl simulate 2 scripts/clienttest.fish

test:
	perl fishnet.pl simulate 2 scripts/transfertest.fish
