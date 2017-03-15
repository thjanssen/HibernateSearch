package org.thoughts.on.java.date;

import java.io.IOException;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.engine.spi.FacetManager;
import org.hibernate.search.query.facet.Facet;
import org.hibernate.search.query.facet.FacetSelection;
import org.hibernate.search.query.facet.FacetSortOrder;
import org.hibernate.search.query.facet.FacetingRequest;
import org.hibernate.search.util.AnalyzerUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thoughts.on.java.model.Tweet;
import org.thoughts.on.java.model.Tweet_;

public class TestSearchTweets {

	Logger log = Logger.getLogger(this.getClass().getName());

	private EntityManagerFactory emf;

	@Before
	public void init() throws InterruptedException {
		emf = Persistence.createEntityManagerFactory("my-persistence-unit");
		
		EntityManager em = emf.createEntityManager();
		FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(em);
		fullTextEntityManager.createIndexer().startAndWait();
	}

	@After
	public void close() {
		emf.close();
	}

	@Test
	public void testSimpleFullTextSearch() {
		log.info("... testSimpleFullTextSearch ...");

		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		
		List<Tweet> results = fullTextQuery(em, "validate Hibernate");
		
		em.getTransaction().commit();
		em.close();
	}
	
	@Test
	public void testIndexUpdate() {
		log.info("... testIndexUpdate ...");

		// Session 1: Check that no tweet matches the search string
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		
		FullTextEntityManager fullTextEm = Search.getFullTextEntityManager(em);
		QueryBuilder tweetQb = fullTextEm.getSearchFactory().buildQueryBuilder().forEntity(Tweet.class).get();
		Query fullTextQuery = tweetQb.keyword().onField(Tweet_.message.getName()).matching("Message updated").createQuery();
		List<Tweet> results = fullTextEm.createFullTextQuery(fullTextQuery).getResultList();
		Assert.assertEquals(0, results.size());
		
		em.getTransaction().commit();
		em.close();
		
		
		// Session 2: Update a tweet
		em = emf.createEntityManager();
		em.getTransaction().begin();
		
		Tweet tweet = em.find(Tweet.class, 1L);
		tweet.setMessage("Message updated - "+tweet.getMessage());
		
		em.getTransaction().commit();
		em.close();
		
		
		// Session 3: Check that 1 tweet matches the search string
		em = emf.createEntityManager();
		em.getTransaction().begin();
		
		fullTextEm = Search.getFullTextEntityManager(em);
		tweetQb = fullTextEm.getSearchFactory().buildQueryBuilder().forEntity(Tweet.class).get();
		fullTextQuery = tweetQb.keyword().onField(Tweet_.message.getName()).matching("Message updated").createQuery();
		results = fullTextEm.createFullTextQuery(fullTextQuery).getResultList();
		Assert.assertEquals(1, results.size());
		
		for (Tweet r : results) {
			log.info(r);
		}
		
		em.getTransaction().commit();
		em.close();
	}
	
	@Test
	public void testStemming() {
		log.info("... testStemming ...");

		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		
		List<Tweet> results1 = fullTextQuery(em, "validate Hibernate");
		List<Tweet> results2 = fullTextQuery(em, "validation Hibernate");
		List<Tweet> results3 = fullTextQuery(em, "VALIDATION Hibernate");
		
		Assert.assertArrayEquals(results1.toArray(), results2.toArray());
		Assert.assertArrayEquals(results2.toArray(), results3.toArray());
		
		em.getTransaction().commit();
		em.close();
	}

	private List<Tweet> fullTextQuery(EntityManager em, String searchTerm) {
		FullTextEntityManager fullTextEm = Search.getFullTextEntityManager(em);
		QueryBuilder tweetQb = fullTextEm.getSearchFactory().buildQueryBuilder().forEntity(Tweet.class).get();
		Query fullTextQuery = tweetQb.keyword().onField(Tweet_.message.getName()).matching(searchTerm).createQuery();
		List<Tweet> results = fullTextEm.createFullTextQuery(fullTextQuery, Tweet.class).getResultList();
		Assert.assertEquals(2, results.size());
		for (Tweet r : results) {
			log.info(r);
		}
		return results;
	}
	
	@Test
	public void analyzerUtils() throws IOException {
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		
		FullTextEntityManager fullTextEm = Search.getFullTextEntityManager(em);
		Analyzer analyzer = fullTextEm.getSearchFactory().getAnalyzer("textanalyzer");
		List<String> terms = AnalyzerUtils.tokenizedTermValues(analyzer, "message", "How to automatically validate entities with Hibernate Validator");
		System.out.println(terms);
		em.getTransaction().commit();
		em.close();
	}
	
	@Test
	public void testUserNameFaceting() {
		log.info("... testUserNameFaceting ...");

		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		
		FullTextEntityManager fullTextEm = Search.getFullTextEntityManager(em);
		QueryBuilder tweetQb = fullTextEm.getSearchFactory().buildQueryBuilder().forEntity(Tweet.class).get();
		Query tweetQuery = tweetQb.all().createQuery();
		FullTextQuery fullTextQuery = fullTextEm.createFullTextQuery(tweetQuery, Tweet.class);
		
		
		FacetingRequest userNameFR = tweetQb.facet()
			    .name("userNameFR")
			    .onField(Tweet_.userName.getName())
			    .discrete()
			    .includeZeroCounts(false)
			    .maxFacetCount(3)
			    .createFacetingRequest();	
		
		FacetManager facetMgr = fullTextQuery.getFacetManager();
		facetMgr.enableFaceting(userNameFR);
		List<Facet> facets = facetMgr.getFacets("userNameFR");
		
		for (Facet f : facets) {
			log.info(f.getValue() + " " + f.getCount());
		}
		
		
		em.getTransaction().commit();
		em.close();
	}
	
	@Test
	public void testFacetingDrillDown() {
		log.info("... testFacetingDrillDown ...");

		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		FullTextEntityManager fullTextEm = Search.getFullTextEntityManager(em);
		
		QueryBuilder tweetQb = fullTextEm.getSearchFactory().buildQueryBuilder().forEntity(Tweet.class).get();
		Query tweetQuery = tweetQb.all().createQuery();
		FullTextQuery fullTextQuery = fullTextEm.createFullTextQuery(tweetQuery, Tweet.class);
		
		
		FacetingRequest userNameFR = tweetQb.facet()
			    .name("userNameFR")
			    .onField(Tweet_.userName.getName())
			    .discrete()
			    .orderedBy(FacetSortOrder.COUNT_DESC)
			    .includeZeroCounts(false)
			    .maxFacetCount(3)
			    .createFacetingRequest();	
		
		FacetManager facetMgr = fullTextQuery.getFacetManager();
		facetMgr.enableFaceting(userNameFR);
		List<Facet> facets = facetMgr.getFacets("userNameFR");
		
		log.info("Selected Facets");
		for (Facet f : facets) {
			log.info(f.getValue() + " " + f.getCount());
		}
		
		
		FacetSelection facetSelection = facetMgr.getFacetGroup( "userNameFR" );
		facetSelection.selectFacets( facets.get( 0 ) );
		
		List<Tweet> tweets = fullTextQuery.getResultList();
		
		log.info("Tweets of Facet ["+facets.get(0).getValue()+"]");
		for (Tweet t : tweets) {
			log.info(t);
		}
		
		em.getTransaction().commit();
		em.close();
	}
	
	@Test
	public void testPostedAtFaceting() {
		log.info("... testPostedAtFaceting ...");

		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		
		FullTextEntityManager fullTextEm = Search.getFullTextEntityManager(em);
		QueryBuilder tweetQb = fullTextEm.getSearchFactory().buildQueryBuilder().forEntity(Tweet.class).get();
		Query tweetQuery = tweetQb.all().createQuery();
		FullTextQuery fullTextQuery = fullTextEm.createFullTextQuery(tweetQuery, Tweet.class);
		
		
		FacetingRequest postedAtFR = tweetQb.facet()
			    .name("postedAtFR")
			    .onField(Tweet_.postedAt.getName())
			    .discrete()
			    .includeZeroCounts(false)
			    .maxFacetCount(3)
			    .createFacetingRequest();	
		
		FacetManager facetMgr = fullTextQuery.getFacetManager();
		facetMgr.enableFaceting(postedAtFR);
		List<Facet> facets = facetMgr.getFacets("postedAtFR");
		
		for (Facet f : facets) {
			log.info(f.getValue() + " " + f.getCount());
		}
		
		
		em.getTransaction().commit();
		em.close();
	}
}
