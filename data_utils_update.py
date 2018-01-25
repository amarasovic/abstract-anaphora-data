from gensim.models.keyedvectors import KeyedVectors
from nltk.tokenize import word_tokenize
from collections import Counter
import numpy as np
import logging
import itertools
import ijson
import json
import codecs

PAD = "<PAD>"
UNK = "<UNK>"


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
            orig_sent_anaph = []
            anaph = [] # anaphor / shell noun
            ctx_all = [] # context of the anaphor/shell noun
            positive_candidates_all = []
            negative_candidates_all = []
            positive_candidates_tag_all = []
            negative_candidates_tag_all = []
            sbarhead_all = []

            for item in ijson.items(open(filename, 'r'), "item"):
                if item['anaphor_head'] == 'none':
                    continue
                anaphoric_sentence = word_tokenize(item['artificial_source_suggestion'].lower().replace("-x-", ""))

                # ignore anaphoric sentences with length (in tokens) < 10
                if len(anaphoric_sentence) < 10:
                    continue

                positive_candidates = [candidate.lower() for candidate in item['antecedents']]
                positive_candidate_tags = item['antecedent_nodes']

                # assert len(list(set(positive_candidates))) == len(list(positive_candidates))

                if not positive_candidates:
                    continue

                # tokenize: word_tokenize ignores extra whitespaces
                positive_candidates_tokenize = [word_tokenize(candidate.lower()) for candidate in positive_candidates]

                if size == "small":
                    negative_candidates = [candidate.lower() for candidate in item['candidates']]
                    negative_candidates_tag = item['candidates_nodes']
                else:
                    negative_candidates = []
                    negative_candidates_tag = []
                    for i in range(int(size.split("_")[1])+1):
                        negative_candidates.extend([candidate.lower() for candidate in item['candidates_'+str(i)]])
                        negative_candidates_tag.extend(item['candidates_nodes_'+str(i)])

                #assert len(list(set(negative_candidates))) == len(list(negative_candidates))

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
                sbarhead_all.append(item['anaphor_derived_from'])
                negative_candidates_all.append(negative_candidates_tokenize)
                negative_candidates_tag_all.append(negative_candidates_tag)
                positive_candidates_all.append(positive_candidates_tokenize)
                positive_candidates_tag_all.append(positive_candidate_tags)

                original_sent = item['original_sentence']
                filter_tokens = ["0", "*", "*T*", "*U*", "*?*", "*PRO*", "*RNR*", "*ICH*", "*EXP*", "*NOT*", "-LRB-", "-RRB-"]
                original_sent_clean = [w.lower() for w in word_tokenize(original_sent) if w not in filter_tokens]
                original_sent_clean_str = ' '.join(original_sent_clean)
                sentences.append(original_sent_clean)
                if size.split("_")[0] != 'small':
                    distance = int(size.split("_")[1])
                    prev_context = item['prev_context']
                    for dist, sent in enumerate(prev_context):
                        if dist <= distance:
                            sentences.append(word_tokenize(sent.lower()))
                orig_sent_anaph.append(original_sent_clean_str)

            data = zip(anaph, sent_anaph,
                       positive_candidates_all, negative_candidates_all,
                       positive_candidates_tag_all, negative_candidates_tag_all,
                       ctx_all, sbarhead_all, orig_sent_anaph)

            assert data
            dict_train = {'dataset_sentences': sentences,
                          'data': data}

            with open("data/" + dataset + "_" + size + '.json', 'w') as fp:
                json.dump(dict_train, fp)

            #for atype in ['NONE', 'that', 'this', '0', 'because', 'while', 'if-adv', 'since-tmp', 'since-prp',
            #              'as-tmp', 'as-prp', 'whether', 'after', 'until']:
            for atype in ['if-adv', 'since-prp', 'as-tmp', 'as-prp', 'whether']:
                eval_sample_file = open('eval_data/eval_' + atype + '.txt', 'w')
                for item in data:
                    if item[7] == atype:
                        pos_candidates = ''.join(['(' + str(k) + ') ' + ' '.join(cand) + ', ' for k, cand in enumerate(item[2])])
                        eval_sample_file.write('\t'.join([item[7],
                                                          item[0][0],
                                                          item[8],
                                                          ' '.join(item[1]),
                                                          pos_candidates]) + '\n')
                eval_sample_file.close()


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


def vectorize_json(vocabulary, pos_vocabulary, data):
    '''
    Final preparation of the data for the LSTM-Siamese mention ranking model: words -> vocab ids
    '''
    global UNK
    global PAD
    anaph_str, sent_anaph_str, \
    pos_cands_str, neg_cands_str, \
    pos_cand_tags_str, neg_cand_tags_str,\
    ctx_str, _, _ = zip(*data)

    anaph = [vocabulary[a] if a in vocabulary else vocabulary[UNK] for a in anaph_str[0]]
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


if __name__ == '__main__':
    make_model_input(['wsj'], ['small'])

    json_file = 'data/wsj_small.json'
    with open(json_file) as data_file:
        artificial = json.load(data_file)
    sentences = artificial['dataset_sentences']

    emb, vocab, oov_prec = get_emb_vocab(sentences, 'glove', 100, 1)

    print vocab

    pos_tags_filename = "data/penn_treebank_tags.txt"
    pos_tags_lines = codecs.open(pos_tags_filename, "r", encoding="utf-8").readlines()
    pos_tags = [tag.split("\n")[0] for tag in pos_tags_lines]
    pos_ids = range(len(pos_tags))
    pos_vocabulary = dict(zip(pos_tags, pos_ids))
    pos_vocabulary[UNK] = len(pos_tags)

    dataset = vectorize_json(vocab, pos_vocabulary, artificial['data'])

