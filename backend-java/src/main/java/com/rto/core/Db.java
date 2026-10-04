package com.rto.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Thin data-access helper shared by all services. Named parameters are passed as alternating name/value pairs;
 * "lock" variants issue SELECT ... FOR UPDATE and refresh the entity so state read before the lock is never stale.
 */
@Component
public class Db {

    @PersistenceContext
    private EntityManager em;

    public EntityManager em() { return em; }

    // ---- single rows --------------------------------------------------------------------------------

    public <T> T get(Class<T> type, Object id, String label) {
        T e = id == null ? null : em.find(type, id);
        if (e == null) throw ApiException.notFound(label + " not found");
        return e;
    }

    public <T> T find(Class<T> type, Object id) {
        return id == null ? null : em.find(type, id);
    }

    /** Fetch and row-lock by primary key (FOR UPDATE); 404 when missing. */
    public <T> T lock(Class<T> type, Object id, String label) {
        T e = get(type, id, label);
        em.flush();
        em.refresh(e, LockModeType.PESSIMISTIC_WRITE);
        return e;
    }

    public <T> T save(T entity) {
        em.persist(entity);
        return entity;
    }

    public void flush() { em.flush(); }

    public void refresh(Object entity) { em.refresh(entity); }

    public void remove(Object entity) { em.remove(entity); }

    // ---- queries ------------------------------------------------------------------------------------

    private static void bind(Query q, Object[] kv) {
        for (int i = 0; i < kv.length; i += 2) q.setParameter((String) kv[i], kv[i + 1]);
    }

    public <T> List<T> list(Class<T> type, String jpql, Object... kv) {
        TypedQuery<T> q = em.createQuery(jpql, type);
        bind(q, kv);
        return q.getResultList();
    }

    public <T> T first(Class<T> type, String jpql, Object... kv) {
        TypedQuery<T> q = em.createQuery(jpql, type);
        bind(q, kv);
        q.setMaxResults(1);
        List<T> r = q.getResultList();
        return r.isEmpty() ? null : r.get(0);
    }

    public long count(String jpql, Object... kv) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        bind(q, kv);
        Long v = q.getSingleResult();
        return v == null ? 0 : v;
    }

    public boolean exists(String jpql, Object... kv) {
        return first(Long.class, jpql, kv) != null;
    }

    public List<Object[]> rows(String jpql, Object... kv) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        bind(q, kv);
        return q.getResultList();
    }

    public int update(String jpql, Object... kv) {
        Query q = em.createQuery(jpql);
        bind(q, kv);
        return q.executeUpdate();
    }

    public List<Object[]> nativeRows(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked") List<Object[]> r = q.getResultList();
        return r;
    }

    public Object nativeScalar(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q.getSingleResult();
    }

    public <T> List<T> lockList(Class<T> type, String jpql, Object... kv) {
        TypedQuery<T> q = em.createQuery(jpql, type);
        bind(q, kv);
        q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        List<T> rows = q.getResultList();
        for (T r : rows) em.refresh(r, LockModeType.PESSIMISTIC_WRITE);
        return rows;
    }

    public <T> T lockFirst(Class<T> type, String jpql, Object... kv) {
        TypedQuery<T> q = em.createQuery(jpql, type);
        bind(q, kv);
        q.setMaxResults(1);
        q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        List<T> rows = q.getResultList();
        if (rows.isEmpty()) return null;
        em.refresh(rows.get(0), LockModeType.PESSIMISTIC_WRITE);
        return rows.get(0);
    }

    // ---- pagination -----------------------------------------------------------------------------------

    /**
     * Runs COUNT over the filtered query and fetches one bounded page. {@code sortable} maps public sort keys to JPQL
     * paths (anything else is a 400); {@code defaultPath}/{@code defaultDesc} apply when no sort is requested.
     */
    public <T> PageResponse<T> page(QB qb, Class<T> type, PageParams p, Map<String, String> sortable,
                                    String defaultPath, boolean defaultDesc) {
        String path = defaultPath;
        boolean desc = defaultDesc || p.desc();
        if (p.sort() != null && !p.sort().isBlank()) {
            path = sortable.get(p.sort());
            if (path == null) {
                throw ApiException.badRequest("INVALID_SORT",
                        "Cannot sort by '" + p.sort() + "'. Allowed: " + String.join(", ", new java.util.TreeSet<>(sortable.keySet())));
            }
            desc = p.desc();
        }
        TypedQuery<Long> cq = em.createQuery(qb.countJpql(), Long.class);
        qb.params.forEach(cq::setParameter);
        long total = cq.getSingleResult();
        String tie = defaultPath.equals(path) ? "" : ", " + defaultPath;
        TypedQuery<T> q = em.createQuery(qb.selectJpql() + " order by " + path + (desc ? " desc" : " asc") + tie, type);
        qb.params.forEach(q::setParameter);
        q.setFirstResult(p.offset());
        q.setMaxResults(p.pageSize());
        return PageResponse.of(q.getResultList(), p, total);
    }
}
