package org.hibernate.bugs;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
public class JPAUnitTestCase {
	private static final Logger LOG = LogManager.getLogger(JPAUnitTestCase.class);

	private EntityManagerFactory entityManagerFactory;

	@Before
	public void init() {
		entityManagerFactory = Persistence.createEntityManagerFactory( "templatePU" );

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();


		final var accountParent = new Account();
		accountParent.setName("Parent Account");

		final var accountChild = new Account();
		accountChild.setName("Child Account");
		accountChild.setParent(accountParent);

		entityManager.persist(accountParent);
		entityManager.persist(accountChild);

		entityManager.getTransaction().commit();
		entityManager.close();

	}

	@After
	public void destroy() {
		entityManagerFactory.close();
	}

	// Entities are auto-discovered, so just add them anywhere on class-path
	// Add your tests, using standard JUnit.
	@Test
	public void successTest() throws Exception {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

    final var query = entityManager.createQuery(
        """
  with accountChainTable as(
    select child.id id, child.name name, child.parent.id parent_id from Account child where lower(child.name) like '%account%'
    union all
    select account.id, account.name, account.parent.id from Account account, accountChainTable chain
      where account.id = chain.parent_id
  )
  select c.id, c.name, c.parent_id from accountChainTable c
  """);

	for (final var account : query.getResultList()) {
		LOG.info("Account: {}", account);
	}

		entityManager.getTransaction().commit();
		entityManager.close();
	}

  /**
   * java.lang.IllegalArgumentException: Already registered a copy: org.hibernate.query.sqm.tree.cte.SqmCteStatement@5844a2d1
   */
  @Test
  public void failedTest() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();
		final var builder = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

		final var query = builder.createTupleQuery();

		final var nonRecurQuery = builder.createTupleQuery();
		final var nonRecurRoot = nonRecurQuery.from(Account.class);
		nonRecurQuery.multiselect(
				nonRecurRoot.get("id").alias("id"),
				nonRecurRoot.get("name").alias("name"),
				nonRecurRoot
						.get("parent")
						.get("id")
						.alias("parent_id"));
		nonRecurQuery.where(
				builder.like(
						builder.lower(nonRecurRoot.get("name")), "%account%"));
		final var accountChainTable =
				query.withRecursiveUnionAll(
						nonRecurQuery,
						(cte) -> {
							final var innerQuery = builder.createTupleQuery();
							final var accountRoot = innerQuery.from(Account.class);
							final var cteRoot = innerQuery.from(cte);
							innerQuery.multiselect(
									accountRoot.get("id"),
									accountRoot.get("name"),
									accountRoot.get("parent").get("id"));
							innerQuery.where(
									builder.equal(
											accountRoot.get("id"),
											cteRoot.get("parent_id")));
							return innerQuery;
						});

		final var root = query.from(accountChainTable);
		query.multiselect(
				root.get("id"), root.get("name"), root.get("parent_id"));

		for (final var tuple : entityManager.createQuery(query).getResultList()) {
			LOG.info("Account: {}", tuple);
		}

		entityManager.getTransaction().commit();
		entityManager.close();
	}
}
