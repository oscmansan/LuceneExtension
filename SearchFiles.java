/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;

/** Simple command-line based search demo. */
public class SearchFiles {

  public static int k = 5;
  public static int N = 10;
  public static double a = 0.75;
  public static double b = 0.25;
  public static int nrounds = 5;

  private SearchFiles() {}

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/java/4_0/demo.html for details.";
    if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = "index";
    String field = "contents";
    String queries = null;
    int repeat = 0;
    boolean raw = false;
    String queryString = null;
    int hitsPerPage = 10;
    
    for(int i = 0;i < args.length;i++) {
      if ("-index".equals(args[i])) {
        index = args[i+1];
        i++;
      } else if ("-field".equals(args[i])) {
        field = args[i+1];
        i++;
      } else if ("-queries".equals(args[i])) {
        queries = args[i+1];
        i++;
      } else if ("-query".equals(args[i])) {
        queryString = args[i+1];
        i++;
      } else if ("-repeat".equals(args[i])) {
        repeat = Integer.parseInt(args[i+1]);
        i++;
      } else if ("-raw".equals(args[i])) {
        raw = true;
      } else if ("-paging".equals(args[i])) {
        hitsPerPage = Integer.parseInt(args[i+1]);
        if (hitsPerPage <= 0) {
          System.err.println("There must be at least 1 hit per page.");
          System.exit(1);
        }
        i++;
      }
    }
    
    IndexReader reader = IndexReader.open(FSDirectory.open(new File(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_31);

    BufferedReader in = null;
    if (queries != null) {
      in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), "UTF-8"));
    } else {
      in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    }
    QueryParser parser = new QueryParser(Version.LUCENE_31, field, analyzer);
    while (true) {
      if (queries == null && queryString == null) {                        // prompt the user
        System.out.println("Enter query: ");
      }

      String line = queryString != null ? queryString : in.readLine();

      if (line == null || line.length() == -1) {
        break;
      }

      line = line.trim();
      if (line.length() == 0) {
        break;
      }
      
      Query query = parser.parse(line);
      System.out.println("Searching for: " + query.toString(field));
            
      if (repeat > 0) {                           // repeat & time as benchmark
        Date start = new Date();
        for (int i = 0; i < repeat; i++) {
          searcher.search(query, null, 100);
        }
        Date end = new Date();
        System.out.println("Time: "+(end.getTime()-start.getTime())+"ms");
      }

      query = userRelevanceFeedback(query,searcher,nrounds);

      Set<Term> queryTerms = new HashSet<Term>();
      query.extractTerms(queryTerms);
      System.out.print("[");
      for (Term t : queryTerms) {
        System.out.print(t.text() + " ");
      }
      System.out.print("]\n");

      doPagingSearch(in, searcher, query, hitsPerPage, raw, queries == null && queryString == null);

      if (queryString != null) {
        break;
      }
    }
    searcher.close();
    reader.close();
  }

  // Structure to store and retrieve idf terms efficiently
  private static HashMap<String,Double> idfs = new HashMap<>();

  // returns the number of documents where string s appears
  private static int docFreq(IndexReader reader, String s) throws Exception {
    return reader.docFreq(new Term("contents",s));
  }

  // Returns an array of TermWeights representing
  // the document whose identifier in reader is docId in tf-idf format,
  // with base 10 logs.
  // The vector is not normalized (may have length != 1)
  private static TermWeight[] toTfIdf(IndexReader reader, int docId) throws Exception {
    // get Lucene representation of a Term-Frequency vector
    TermFreqVector tfv = reader.getTermFreqVector(docId,"contents");

    // split it into two Arrays: one for terms, one for frequencies;
    // Lucene guarantees that terms are sorted
    String[] terms = tfv.getTerms();
    int[] freqs = tfv.getTermFrequencies();

    TermWeight[] tw = new TermWeight[terms.length];

    // compute the maximum frequence of a term in the document
    int fmax = freqs[0];
    for (int i = 1; i < freqs.length; i++) {
      if (freqs[i] > fmax) fmax = freqs[i];
    }

    // number of docs in the index
    int nDocs = reader.numDocs();

    for (int i = 0; i < tw.length; i++) {
      //... code to compute stuff ...
      String term = terms[i];
      double tf = (double)freqs[i] / (double)fmax;
      double idf;
      if (idfs.containsKey(term)) {
        idf = idfs.get(term);
      }
      else {
        double df = docFreq(reader,term);
        idf = Math.log10(nDocs / df);
        idfs.put(term,idf);
      }
      double w = tf*idf;

      tw[i] = new TermWeight(term,w);
    }

    return tw;
  }

  // Normalizes the weights in t so that they form a unit-length vector
  // It is assumed that not all weights are 0
  private static void normalize(TermWeight[] t) {
    double norm = 0;
    for (TermWeight tw : t) {
      norm += Math.pow(tw.getWeight(),2);
    }
    norm = Math.sqrt(norm);

    for (int i = 0; i < t.length; i++) {
      t[i].setWeight(t[i].getWeight() / norm);
    }
  }

  // prints the list of pairs (term,weight) in v
  private static void printTermWeightVector(TermWeight[] v) {
    for (TermWeight tw : v) {
      System.out.println(tw.getText() + " " + tw.getWeight());
    }
  }

  // Adds two TermWeight vectors in compressed form
  private static TermWeight[] add(TermWeight[] v1, TermWeight[] v2) {
    int i = 0;
    int j = 0;
    ArrayList<TermWeight> sum = new ArrayList<>();
    while (i < v1.length && j < v2.length) {
      String t1 = v1[i].getText();
      String t2 = v2[j].getText();
      int cmp = t1.compareTo(t2);

      if (cmp < 0) {
        sum.add(new TermWeight(t1, v1[i].getWeight()));
        ++i;
      }
      else if (cmp > 0) {
        sum.add(new TermWeight(t2, v2[j].getWeight()));
        ++j;
      }
      else {
        sum.add(new TermWeight(t1, v1[i].getWeight() + v2[j].getWeight()));
        ++i; ++j;
      }
    }

    // Convert to array
    TermWeight[] res = new TermWeight[sum.size()];
    sum.toArray(res);
    return res;
  }

  // Multiplies a TermWeight vector by a constant
  private static TermWeight[] multByConst(double a, TermWeight[] v) {
    TermWeight[] res = new TermWeight[v.length];
    for (int i = 0; i < v.length; ++i) {
      res[i] = new TermWeight(v[i].getText(), a*v[i].getWeight());
    }
    return res;
  }

  // Removes all but the N heaviest components from the query
  private static TermWeight[] Purge(TermWeight[] query) {
    ArrayList<TermWeight> tmp = new ArrayList<>(Arrays.asList(query));
    Collections.sort(tmp, new Comparator<TermWeight>() {
      @Override
      public int compare(final TermWeight tw1, final TermWeight tw2) {
        double w1 = tw1.getWeight();
        double w2 = tw2.getWeight();
        if (w1 != w2) {
          return (w1 < w2) ? 1 : -1; // descending order
        }
        else {
          String t1 = tw1.getText();
          String t2 = tw2.getText();
          return t2.compareTo(t1);   // descending order
        }
      }
    });

    query = new TermWeight[N];
    for (int i = 0; i < N; ++i)
      query[i] = tmp.get(i);

    return query;
  }

  // With the given query and the list of results, it computes a new query using Rocchio's rule
  private static Query Rocchio(Query query, TopDocs results, int k, IndexReader reader) throws Exception {
    k = Math.min(k,results.scoreDocs.length);
    if (k == 0) return query;

    // Get document vectors
    TermWeight[][] docs = new TermWeight[k][];
    ScoreDoc[] scoreDocs = results.scoreDocs;
    for (int i = 0; i < k; ++i) {
      ScoreDoc scoreDoc = scoreDocs[i];
      int docId = scoreDoc.doc;
      docs[i] = toTfIdf(reader,docId);
      normalize(docs[i]);
    }

    // Get query vector
    Set<Term> queryTerms = new HashSet<Term>();
    query.extractTerms(queryTerms);
    TermWeight[] qv = new TermWeight[queryTerms.size()];
    int j = 0;
    for (Term t : queryTerms) {
        qv[j] = new TermWeight(t.text(),1);
        ++j;
    }
    normalize(qv);

    // Apply Rocchio's rule
    TermWeight[] R1 = multByConst(a,qv);

    TermWeight[] sum = docs[0];
    for (int i = 1; i < k; ++i) {
        sum = add(sum,docs[i]);
    }
    TermWeight[] R2 = multByConst(b/k,sum);

    TermWeight[] newQuery = add(R1,R2);

    // Purge new query
    newQuery = Purge(newQuery);

    // Transform the TermWeight array into an instance of Lucene Query class
    StringBuilder sb = new StringBuilder();
    for (TermWeight tw : newQuery) {
      sb.append(tw.getText() + "^" + tw.getWeight() + " ");
    }

    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_31);
    QueryParser parser = new QueryParser(Version.LUCENE_31, "contents", analyzer);
    query = parser.parse(sb.toString());

    return query;
  }

  public static Query userRelevanceFeedback(Query query, IndexSearcher searcher, int nrounds) throws Exception {
      for (int i = 1; i < nrounds; i++) {
        TopDocs results;
        try {
          results = searcher.search(query, k);
        }
        catch (Exception e) {
          break;
        }
        k = Math.min(k,results.totalHits);
        IndexReader reader = searcher.getIndexReader();
        query = Rocchio(query, results, k, reader);
      }

      return query;
  }

  /**
   * This demonstrates a typical paging search scenario, where the search engine presents 
   * pages of size n to the user. The user can then go to the next page if interested in
   * the next hits.
   * 
   * When the query is executed for the first time, then only enough results are collected
   * to fill 5 result pages. If the user wants to page beyond this limit, then the query
   * is executed another time and all hits are collected.
   * 
   */
  public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, 
                                     int hitsPerPage, boolean raw, boolean interactive) throws IOException {
    // Collect enough docs to show 5 pages
    TopDocs results = searcher.search(query, 5 * hitsPerPage);
    ScoreDoc[] hits = results.scoreDocs;
    
    int numTotalHits = results.totalHits;
    System.out.println(numTotalHits + " total matching documents");

    int start = 0;
    int end = Math.min(numTotalHits, hitsPerPage);
        
    while (true) {
      if (end > hits.length) {
        System.out.println("Only results 1 - " + hits.length +" of " + numTotalHits + " total matching documents collected.");
        System.out.println("Collect more (y/n) ?");
        String line = in.readLine();
        if (line.length() == 0 || line.charAt(0) == 'n') {
          break;
        }

        hits = searcher.search(query, numTotalHits).scoreDocs;
      }
      
      end = Math.min(hits.length, start + hitsPerPage);
      
      for (int i = start; i < end; i++) {
        if (raw) {                              // output raw format
          System.out.println("doc="+hits[i].doc+" score="+hits[i].score);
          continue;
        }

        Document doc = searcher.doc(hits[i].doc);
        String path = doc.get("path");
        if (path != null) {
          System.out.println((i+1) + ". " + path);
          String title = doc.get("title");
          if (title != null) {
            System.out.println("   Title: " + doc.get("title"));
          }
        } else {
          System.out.println((i+1) + ". " + "No path for this document");
        }
                  
      }

      if (!interactive || end == 0) {
        break;
      }

      if (numTotalHits >= end) {
        boolean quit = false;
        while (true) {
          System.out.print("Press ");
          if (start - hitsPerPage >= 0) {
            System.out.print("(p)revious page, ");  
          }
          if (start + hitsPerPage < numTotalHits) {
            System.out.print("(n)ext page, ");
          }
          System.out.println("(q)uit or enter number to jump to a page.");
          
          String line = in.readLine();
          if (line.length() == 0 || line.charAt(0)=='q') {
            quit = true;
            break;
          }
          if (line.charAt(0) == 'p') {
            start = Math.max(0, start - hitsPerPage);
            break;
          } else if (line.charAt(0) == 'n') {
            if (start + hitsPerPage < numTotalHits) {
              start+=hitsPerPage;
            }
            break;
          } else {
            int page = Integer.parseInt(line);
            if ((page - 1) * hitsPerPage < numTotalHits) {
              start = (page - 1) * hitsPerPage;
              break;
            } else {
              System.out.println("No such page");
            }
          }
        }
        if (quit) break;
        end = Math.min(numTotalHits, start + hitsPerPage);
      }
    }
  }
}
