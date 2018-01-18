# abstract-anaphora-data
Scripts for processing data for resolution of abstract anaphora

## Generate training data

Follow instructions how to generate training data as in:

Ana Marasović, Leo Born, Juri Opitz, and Anette Frank (2017): [A Mention-Ranking Model for Abstract Anaphora Resolution](http://aclweb.org/anthology/D/D17/D17-1021.pdf). In Proceedings of the 2017 Conference on Empirical Methods in Natural Language Processing (EMNLP). Copenhagen, Denmark.

If you make use of the contents of this repository, please cite the following paper:


	@InProceedings{D17-1021,
	   author={Marasovi\'{c}, Ana and Born, Leo and Opitz, Juri and Frank, Anette},
	   title = "A Mention-Ranking Model for Abstract Anaphora Resolution",
	   booktitle = "Proceedings of the 2017 Conference on Empirical Methods in Natural Language Processing (EMNLP)",
	   year = "2017",
	   publisher = "Association for Computational Linguistics",
	   pages = "221--232",
	   location =  "Copenhagen, Denmark",
	   url = "http://aclweb.org/anthology/D17-1021"
	  }

### Prerequisite

1. download [TigerSearch](http://www.ims.uni-stuttgart.de/forschung/ressourcen/werkzeuge/tigersearch.html)

### Insert a parsed corpus in TigerSearch 
1. run: ```./runTRegistry.sh```
2. click on CorporaDir in the left sidebar
3. from the toolbar choose: Corpus > Insert Corpus
4. import parsed corpus and choose "General Penn treebank format filter"
5. click Start

### Find matches for a syntactic pattern
1. run: ```./runTSearch.sh```
2. click on the corpus in the left sidebar
3. write a query in the big white space and click Search
4. from the toolbar choose: Query > Export Matches to File > Export to file and Submit 


### Used Queries
**(q1)** to get the full corpus:

	[word=/.*/];

**(q2)** for the general VP-SBAR-S pattern without any constraints: 

	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp > #sbar
	& #sbar > #s;

**(q3)** for the VP-SBAR-S pattern _with_ wh-adverb (WHADV), wh-noun (WHNP) or wh-propositional (WHPP) subordinate clause (SBAR):

	#vp & #sbar & #s & #wh 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #wh: [cat=/WHNP\-[0-9A-Z]/ | cat = "WHNP" | cat="WHADJP" | cat=/WHADJP\-[0-9A-Z]/ | cat="WHADVP" | cat=/WHADVP\-[0-9A-Z]/ | cat="WHPP" | cat=/WHPP\-[0-9A-Z]/]
	& #vp > #sbar
	& #sbar > #s
	& #sbar > #wh;

**Filtered examples**: 

* WHNP: That selling of futures contract by elevators [is [what [helps keep downward pressure on crop prices during the harves]_S]_SBAR]_VP.
* WHADJP: But some analysts [wonder [how [strong the recovery will be]_S]_SBAR]_VP.
* WHADVP: Predictions for limited dollar losses are based largely on the pound's weak state after Mr. Lawson's resignation and the yen's inability to [strengthen substantially [when [there are dollar retreats]_S]_SBAR]_VP.
* WHPP: He said, while dialogue is important, enough forums already [exist [in which [different intrests can express themselve]_S]_SBAR]_VP.

**(q4)** for the VP-SBAR-S pattern WITH "that" as the head of the SBAR clause we require that the VP has exactly 2 children (to avoid relative clauses); others are captured with:
	
	#vp & #sbar & #s  
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp > #sbar
	& #sbar > #s
	& #sbar > [word="that"]
	& arity(#vp, 3, 100);

**A filtered example:**

The FDA already requires drug manufactures to [include [warnings *ICH*]_NP [with [insulin products]_NP]_PP [that [symptoms of hypoglycemia are less pronounced with human insulin than with animal-based products]_S]_SBAR. 

**(q5)** for the general VP-SBAR-S pattern we capture more relative clauses with this pattern: 

	#vp & #sbar1 & #s1 & #sbar2 & #s2 
	& #vp: [cat="VP"] 
	& #sbar1: [cat="SBAR"]
	& #sbar2: [cat="SBAR"]
	& #s1: [cat="S"]
	& #s2: [cat="S"]
	& #wh: [cat=/WHNP\-[0-9A-Z]/ | cat = "WHNP" | cat="WHADJP" | cat=/WHADJP\-[0-9A-Z]/ | cat="WHADVP" | cat=/WHADVP\-[0-9A-Z]/ | cat="WHPP" | cat=/WHPP\-[0-9A-Z]/]
	& #sbar1 > #wh
	& #sbar1 > #s1
	& #s1 > #vp
	& #vp > #sbar2
	& #sbar2 > #s2 

**Filtered example:**

Under the direction of its new chairman, Francisco Luzon, Spain's seventh largest bank is undergoing a tough restructuring [[that]_WHNP [analysts [say [[may be the first step toward the bank's privatization]_S]_SBAR2]_VP]_S1]_SBAR1

**(q6)** for temporal "since" examples:

	#root & #sbar & #s 
	& #root: [cat=/.*/] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #root >TMP #sbar
	& #sbar > #s
	& #sbar > [word="since"];

**(q7)** for purpose/reason "since" examples:

	#root & #sbar & #s 
	& #root: [cat=/.*/] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #root >PRP #sbar
	& #sbar > #s
	& #sbar > [word="since"];

**(q8)** for all "since" examples for which the parrent of the SBAR node is a VP:
	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp > #sbar
	& #sbar > #s
	& #sbar > [word="since"];

**(q9)** for temporal "as" examples:

	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp >TMP #sbar
	& #sbar > #s
	& #sbar > [word="as"];

**(q10)** for purpose/reason "as" examples:

	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp >PRP #sbar
	& #sbar > #s
	& #sbar > [word="as"];


**(q11)** for all "as" examples:
	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp > #sbar
	& #sbar > #s
	& #sbar > [word="as"];

**(q12)** for adv "if" examples for which the SBAR node has only 2 children:
	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp >ADV #sbar
	& #sbar > #s
	& #sbar > [word="if"]
	& arity(#sbar, 2);

**(q13)** for all "if" examples:
	#vp & #sbar & #s 
	& #vp: [cat="VP"] 
	& #sbar: [cat="SBAR"]
	& #s: [cat="S"]
	& #vp > #sbar
	& #sbar > #s
	& #sbar > [word="if"];


### Export matches

Export Matches to File: tigersearch_matches/corpus_name + _ + query_name, e.g. tigersearch_matches/wsj_full (without .xml). It is important for next steps that files are saved exactly in this format.

* q1 - full
* q2 - general
* q3 - wh
* q4 - that
* q5 - relative
* q6 - since_tmp
* q7 - since_prp
* q8 - since_vp
* q9 - as_tmp
* q10 - as_prp
* q11 - as_vp
* q12 - if_adv
* q13 - if_vp


### Make json #1

``````

