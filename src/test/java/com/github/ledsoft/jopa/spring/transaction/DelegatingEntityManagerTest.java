package com.github.ledsoft.jopa.spring.transaction;

import com.github.ledsoft.jopa.spring.transaction.model.Person;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DelegatingEntityManagerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DelegatingEntityManager sut = new DelegatingEntityManager();

    @Test
    public void delegatesCallsToCurrentEntityManager() {
        final EntityManager em = mock(EntityManager.class);
        sut.setLocalTransaction(new JopaTransactionDefinition(em));

        final Person instance = new Person();
        sut.persist(instance);
        verify(em).persist(instance);
    }

    @Test
    public void findOpensNonTransactionalManagerToPerformOperation() throws Exception {
        final EntityManager em = mock(EntityManager.class);
        final EntityManagerFactory emfMock = mock(EntityManagerFactory.class);
        when(emfMock.createEntityManager()).thenReturn(em);
        final URI uri = URI.create("http://www.example.org/PersonOne");
        final Person instance = new Person();
        when(em.find(Person.class, uri)).thenReturn(instance);
        sut.setEntityManagerProvider(new EntityManagerProvider(emfMock));

        final Person p = sut.find(Person.class, uri);
        assertSame(instance, p);
        verify(em).close();
    }

    @Test
    public void getEntityManagerFactoryDoesNotRequireTransactionalEntityManager() {
        final EntityManagerFactory emfMock = mock(EntityManagerFactory.class);
        final EntityManagerProvider provider = spy(new EntityManagerProvider(emfMock));
        sut.setEntityManagerProvider(provider);
        final EntityManagerFactory result = sut.getEntityManagerFactory();
        assertSame(emfMock, result);
        verify(provider).getEntityManagerFactory();
        verify(provider, never()).createEntityManager();
    }

    @Test
    public void getMetamodelDoesNotRequireTransactionalEntityManager() {
        final Metamodel metamodelMock = mock(Metamodel.class);
        final EntityManagerFactory emfMock = mock(EntityManagerFactory.class);
        when(emfMock.getMetamodel()).thenReturn(metamodelMock);
        final EntityManagerProvider provider = spy(new EntityManagerProvider(emfMock));
        sut.setEntityManagerProvider(provider);
        final Metamodel result = sut.getMetamodel();
        assertSame(metamodelMock, result);
        verify(provider).getEntityManagerFactory();
        verify(provider, never()).createEntityManager();
    }

    @Test
    public void twoThreadsWorkWithDifferentTransactionManagers() throws Exception {
        final EntityManager emOne = mock(EntityManager.class);
        final EntityManager emTwo = mock(EntityManager.class);
        final CountDownLatch latch = new CountDownLatch(2);
        final Thread tOne = new Thread(new TestTransaction(emOne, latch));
        final Thread tTwo = new Thread(new TestTransaction(emTwo, latch));
        tOne.start();
        tTwo.start();
        latch.await();

        verify(emOne).persist(any());
        verify(emOne).contains(any());
        verify(emTwo).persist(any());
        verify(emTwo).contains(any());
    }

    private class TestTransaction implements Runnable {

        private EntityManager em;
        private CountDownLatch countDown;

        TestTransaction(EntityManager em, CountDownLatch countDown) {
            this.em = em;
            this.countDown = countDown;
        }

        @Override
        public void run() {
            DelegatingEntityManagerTest.this.sut.setLocalTransaction(new JopaTransactionDefinition(em));
            DelegatingEntityManagerTest.this.sut.persist(new Person());
            DelegatingEntityManagerTest.this.sut.contains("Test");
            countDown.countDown();
        }
    }
}