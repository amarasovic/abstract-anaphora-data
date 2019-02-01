# README #

This package contains the java classes (Java v1.8) used to generate the ARRAU PA data set and the CSN data set.

## Structure ###
arrau_csn/arrau/rst:	ArrauPAConstructor
				ArrauPAStatistics
				PreviousSentenceBaseline
				
arrau_csn/csn:			CSNConstructor
				CSNPostProcessing
				NYTMappingDeterminator
				NYTMappingResolver
				
arrau_csn/general:		RemoveDuplicates

NOTE: The recommended execution order for arrau_csn/csn is:
1) CSNConstructor, to get the base .json file without context information
2) NYTMappingDeterminator, to get the mappings between instance sentences and originating files in NYT corpus 
3) NYTMappingResolver, to use the mappings from 2) to enrich the JSON file from 1) with context information
4) CSNPostProcessing, to post-process the complete CSN JSON file output from 3)

### External Dependencies ###
* json-simple-1.1.1 (https://code.google.com/archive/p/json-simple/downloads#!)
* Stanford CoreNLP v3.7.0 and its modles (https://stanfordnlp.github.io/CoreNLP/history.html)
* The official helper tools of the NYT corpus (https://catalog.ldc.upenn.edu/LDC2008T19)

The last one (NYT helper tools) were copied directly into the project (package "com" on the same level as this package, "pa"); the other tools were simply included in the classpath as .jar archives.

### Contact ###
born [at] cl.uni-heidelberg.de