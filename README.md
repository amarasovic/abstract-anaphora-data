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
	& #sbar2 > #s2; 

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


### Make json 

```python make_artificicial_anaphora_data_fromtigerxml.py corpus_name```

```python post_process.py corpus_name_out corpus_name_new```

Produces a json of the format: 

```
{
        "anaphor": "string", # e.g. "because of that"
        "anaphor_derived_from": "string", # "since-prp"
        "anaphor_head": "string", # "that" 
        "antecedent_nodes": [
            "string", # e.g. "S"
            "string", # e.g. "VP"
        ], 
        "antecedents": [
            "string", 
            "string"
        ], # every other constituent that differs from the extracted antecedent in one word and any number of punctuation is consider to be the antecedent as well
           # constituents that contain information about the head of the SBAR clause (e.g. that) or the verb that embeds the SBAR clause (e.g. said) are filtered from both positive and negative candidates
        "artificial_source": "string", # sentence without the S clause which serves as the antecedent 
        "artificial_source_suggestion": "string", # artificial_source with the anaphor replacement for the S clause
        "candidate_nodes": [ 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of syntactic tag labels for every negative candidate from the sentence which contains the antecedent
        "candidate_nodes_0": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of syntactic tag labels for every negative candidate from the anaphoric sentence
        "candidate_nodes_1": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ],  # list of syntactic tag labels for every negative candidate from the sentence before the anaphoric sentence
        "candidate_nodes_2": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of syntactic tag labels for every negative candidate from the sentence which is two positions before the anaphoric sentence
        "candidate_nodes_3": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string"  
        ], # list of syntactic tag labels for every negative candidate from the sentence which is 3 positions before the anaphoric sentence
        "candidate_nodes_4": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string"   
        ], # list of syntactic tag labels for every negative candidate from the sentence which is 4 positions before the anaphoric sentence
        "candidates": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of string of negative candidates from the sentence which contains the antecedents
        "candidates_0": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of string of negative candidates from the anaphoric sentence
        "candidate_1": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ],  # list of string of negative candidates from the sentence before the anaphoric sentence
        "candidate_2": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of string of negative candidates from the sentence two positions before the anaphoric sentence
        "candidate_3": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of string of negative candidates from the sentence 3 positions before the anaphoric sentence
        "candidate_4": [
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string", 
            "string" 
        ], # list of string of negative candidates from the sentence 4 positions before the anaphoric sentence
        "original_sentence": "string", # the sentence on which the syntactic pattern for extraction was applied
        "path_to_doc": "string", 
        "prev_context": [
        	"string",
            "string",
            "string",
            "string"
        ] # strings of 4 preceding sentences of the anaphoric sentence
    }

```

### Modify json for the LSTM-Siamese mention-ranking model

```
def make_model_input(datasets, candidates_list_size):
    '''
    Prepare data for the LSTM-Siamese mention-ranking model.
    :param datasets: 'artifical_wsj'
    :param candidate_list_size: list, with values in ['small', 'big_0', 'big_1, 'big_2', 'big_3']
                                small if candidates extracted from sentences that contain antecedents,
                                big_0 from the anaphoric sentence,
                                big_x from the sentence which occurs x sentences before the anaphoric sentence, x >= 1
    '''
    global PAD
    global UNK

    cwindow = 2

    for dataset in datasets:
        for size in candidates_list_size:
            filename = "data/" + dataset + ".json"

            sentences = [] # all text sequences for constructing vocabulary
            sent_anaph = [] # anaphoric sentence
            anaph = [] # anaphor / shell noun
            ctx_all = [] # context of the anaphor/shell noun
            positive_candidates_all = []
            negative_candidates_all = []
            positive_candidates_tag_all = []
            negative_candidates_tag_all = []

            for item in ijson.items(open(filename, 'r'), "item"):
                anaphoric_sentence = word_tokenize(item['artificial_source_suggestion'].lower().replace("-x-", ""))

                # ignore anaphoric sentences with length (in tokens) < 10
                if len(anaphoric_sentence) < 10:
                    continue

                positive_candidates = [candidate.lower() for candidate in item['antecedents']]
                positive_candidate_tags = item['antecedent_nodes']

                assert len(list(set(positive_candidates))) == len(list(positive_candidates))
                if not positive_candidates:
                    continue

                # tokenize: word_tokenize ignores extra whitespaces
                positive_candidates_tokenize = [word_tokenize(candidate.lower()) for candidate in positive_candidates]

                if size == "small":
                    negative_candidates = [candidate.lower() for candidate in item['candidates']]
                    negative_candidates_tag = item['candidate_nodes']
                else:
                    negative_candidates = []
                    negative_candidates_tag = []
                    for i in range(int(size.split("_")[1])+1):
                        negative_candidates.extend([candidate.lower() for candidate in item['candidates_'+str(i)]])
                        negative_candidates_tag.extend(item['candidate_nodes_'+str(i)])

                assert len(list(set(negative_candidates))) == len(list(negative_candidates))
                if not negative_candidates:
                    continue

                # tokenize: word_tokenize
                negative_candidates_tokenize = [word_tokenize(candidate.lower()) for candidate in negative_candidates]

                asent_temp = item['artificial_source_suggestion'].replace("-X-", " -X- ")
                asent_token = word_tokenize(asent_temp)
                mark_ids = [mid for mid, x in enumerate(asent_token) if x == "-X-"]

                # only one anaphor should be marked
                if len(mark_ids) > 2:
                    continue

                asent_token.remove("-X-")
                asent_token.remove("-X-")

                ctx = [x.lower() for x in asent_token[max(0, mark_ids[0]-cwindow): min(mark_ids[0]+cwindow+1,
                                                                                       len(asent_token))]]
                ctx_all.append(ctx)
                anaph.append(word_tokenize(item['anaphor_head']))
                sent_anaph.append(anaphoric_sentence)
                negative_candidates_all.append(negative_candidates_tokenize)
                negative_candidates_tag_all.append(negative_candidates_tag)
                positive_candidates_all.append(positive_candidates_tokenize)
                positive_candidates_tag_all.append(positive_candidate_tags)

                sentences.append(anaphoric_sentence)
                if candidates_list_size != 'small':
                    distance = int(candidates_list_size.split('_'))
                    prev_context = item['prev_context']
                    for dist, sent in enumerate(prev_context):
                        if dist <= distance:
                            sentences.append(word_tokenize(sent.lower()))

            data = zip(anaph, sent_anaph,
                       positive_candidates_all, negative_candidates_all,
                       positive_candidates_tag_all, negative_candidates_tag_all,
                       ctx_all)

            assert data
            dict_train = {'dataset_sentences': sentences,
                          'data': data}

            with open("aar_jsons/" + dataset + "_" + size + '.json', 'w') as fp:
                json.dump(dict_train, fp)
```


### Build vocabulary 

```
def word2vec_emb_vocab(vocabulary, dim, emb_type):
    global UNK
    global PAD

    if emb_type == "w2v":
        logging.info("Loading pre-trained w2v binary file...")
        w2v_model = KeyedVectors.load_word2vec_format('../embeddings/GoogleNews-vectors-negative300.bin', binary=True)

    else:
        # convert glove vecs into w2v format: https://github.com/manasRK/glove-gensim/blob/master/glove-gensim.py
        glove_file = "../embeddings/glove/glove_" + str(dim) + "_w2vformat.txt"
        w2v_model = KeyedVectors.load_word2vec_format(glove_file, binary=False)  # GloVe Model

    w2v_vectors = w2v_model.syn0

    logging.info("building embeddings for this dataset...")
    vocab_size = len(vocabulary)
    embeddings = np.zeros((vocab_size, dim), dtype=np.float32)

    embeddings[vocabulary[PAD], :] = np.zeros((1, dim))
    embeddings[vocabulary[UNK], :] = np.mean(w2v_vectors, axis=0).reshape((1, dim))

    emb_oov_count = 0
    embv_count = 0
    for word in vocabulary:
        try:
            embv_count += 1
            embeddings[vocabulary[word], :] = w2v_model[word].reshape((1, dim))
        except KeyError:
            emb_oov_count += 1
            embeddings[vocabulary[word], :] = embeddings[vocabulary[UNK], :]

    oov_prec = emb_oov_count / float(embv_count) * 100
    logging.info("perc. of vocab words w/o a pre-trained embedding: %s" % oov_prec)

    del w2v_model

    assert len(vocabulary) == embeddings.shape[0]

    return embeddings, vocabulary, oov_prec


def get_emb_vocab(sentences, emb_type, dim, word_freq):
    logging.info("Building vocabulary...")
    word_counts = dict(Counter(itertools.chain(*sentences)).most_common())
    word_counts_prune = {k: v for k, v in word_counts.iteritems() if v >= word_freq}
    word_counts_list = zip(word_counts_prune.keys(), word_counts_prune.values())

    vocabulary_inv = [x[0] for x in word_counts_list]
    vocabulary_inv.append(PAD)
    vocabulary_inv.append(UNK)

    vocabulary = {x: i for i, x in enumerate(vocabulary_inv)}

    if emb_type == "w2v":
        emb, vocab, oov_perc = word2vec_emb_vocab(vocabulary, dim, emb_type)

    if emb_type == "glove":
        emb, vocab, oov_perc = word2vec_emb_vocab(vocabulary, dim, emb_type)
    return emb, vocab, oov_perc

```

### Vectorize input for the LSTM-based model 

```
def vectorize_json(vocabulary, pos_vocabulary, data):
    '''
    Final preparation of the data for the LSTM-Siamese mention ranking model: words -> vocab ids
    '''
    global UNK
    global PAD
    anaph_str, sent_anaph_str, \
    pos_cands_str, neg_cands_str, \
    pos_cand_tags_str, neg_cand_tags_str, ctx_str = zip(*data)

    anaph = [vocabulary[a] if a in vocabulary else vocabulary[UNK] for a in anaph_str]
    sent_anaph = [[vocabulary[w] if w in vocabulary else vocabulary[UNK] for w in s] for s in sent_anaph_str]
    ctx = [[vocabulary[w] if w in vocabulary else vocabulary[UNK] for w in c] for c in ctx_str]

    pos_cands = [[[vocabulary[w] if w in vocabulary else vocabulary[UNK] for w in s] for s in c] for c in pos_cands_str]
    pos_cand_tags = [[pos_vocabulary[t] if t in pos_vocabulary else pos_vocabulary[UNK] for t in tags]
                     for tags in pos_cand_tags_str]

    neg_cands = [[[vocabulary[w] if w in vocabulary else vocabulary[UNK] for w in s] for s in c] for c in neg_cands_str]
    neg_cand_tags = [[pos_vocabulary[t] if t in pos_vocabulary else pos_vocabulary[UNK] for t in tags]
                     for tags in neg_cand_tags_str]

    data = zip(anaph, sent_anaph, pos_cands, pos_cand_tags, neg_cands, neg_cand_tags, ctx)

    return data
```

All together:

```
    make_model_input(['artificial_wsj'], ['small'])

    json_file = 'aar_jsons/artificial_wsj_small.json'
    with open(json_file) as data_file:
        artificial = json.load(data_file)
    sentences = artificial['dataset_sentences']

    emb, vocab, oov_prec = get_emb_vocab(sentences, 'w2v', 100, 3)

    pos_tags_filename = "data/penn_treebank_tags.txt"
    pos_tags_lines = codecs.open(pos_tags_filename, "r", encoding="utf-8").readlines()
    pos_tags = [tag.split("\n")[0] for tag in pos_tags_lines]
    pos_ids = range(len(pos_tags))
    pos_vocabulary = dict(zip(pos_tags, pos_ids))
    pos_vocabulary[UNK] = len(pos_tags)

    dataset = vectorize_json(vocab, pos_vocabulary, artificial['data'])
```




