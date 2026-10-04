package com.rto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rto.core.*;
import com.rto.domain.Region;
import com.rto.domain.VehicleManufacturer;
import com.rto.dto.ReferenceDto.RegionNode;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Generic, audited CRUD for the configurable master-data tables with per-resource validation hooks
 * (the equivalent of the reference backend's reference_service.py).
 */
@Service
@Transactional
public class ReferenceService {

    /** Extra rule evaluated after the change is applied to the entity and before it is persisted. */
    public interface Hook {
        void check(Db db, Object entity, boolean isNew);
    }

    public record Resource(Class<?> type, String label, String pk, List<String> search, List<List<String>> unique,
                           Map<String, String> sort, Set<String> nullable, Map<String, String> filters, Hook hook) {
        public String table() {
            return type.getAnnotation(jakarta.persistence.Table.class).name();
        }
    }

    private final Db db;
    private final Audit audit;
    private final ObjectMapper mapper;
    private final Patch patch;

    public ReferenceService(Db db, Audit audit, ObjectMapper mapper, Patch patch) {
        this.db = db;
        this.audit = audit;
        this.mapper = mapper;
        this.patch = patch;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(Object entity) {
        return mapper.convertValue(entity, Map.class);
    }

    private static Object prop(Object entity, String camel) {
        return new BeanWrapperImpl(entity).getPropertyValue(camel);
    }

    private static Long id(Resource r, Object entity) {
        return (Long) prop(entity, r.pk());
    }

    private void dupCheck(Resource r, Object entity, boolean isNew) {
        for (List<String> cols : r.unique()) {
            StringBuilder jpql = new StringBuilder("select e." + r.pk() + " from " + r.type().getSimpleName() + " e where ");
            List<Object> kv = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                jpql.append(i == 0 ? "" : " and ").append("e.").append(cols.get(i)).append(" = :c").append(i);
                kv.add("c" + i);
                kv.add(prop(entity, cols.get(i)));
            }
            if (!isNew) {
                jpql.append(" and e.").append(r.pk()).append(" <> :self");
                kv.add("self");
                kv.add(id(r, entity));
            }
            if (db.first(Long.class, jpql.toString(), kv.toArray()) != null) {
                throw ApiException.conflict("DUPLICATE", r.label() + " with the same "
                        + String.join(", ", cols.stream().map(Names::snake).toList()) + " already exists");
            }
        }
    }

    @Transactional(readOnly = true)
    public <E> PageResponse<E> list(Resource r, Class<E> type, PageParams p, Map<String, Object> filters) {
        QB q = new QB("e", r.type().getSimpleName() + " e");
        if (p.search() != null && !r.search().isEmpty()) q.search(p.search(), r.search().stream().map(s -> "e." + s).toArray(String[]::new));
        filters.forEach((param, value) -> q.eq("e." + r.filters().get(param), value));
        Map<String, String> sort = new LinkedHashMap<>();
        r.sort().forEach((k, v) -> sort.put(k, "e." + v));
        return db.page(q, type, p, sort, "e." + r.pk(), false);
    }

    @Transactional(readOnly = true)
    public <E> E get(Resource r, Class<E> type, Long id) {
        return type.cast(db.get(r.type(), id, r.label()));
    }

    public <E> E create(Resource r, Class<E> type, Object dto, CurrentUser actor) {
        E entity = mapper.convertValue(dto, type);
        if (r.hook() != null) r.hook().check(db, entity, true);
        dupCheck(r, entity, true);
        db.save(entity);
        db.flush();
        audit.record(r.table(), id(r, entity), "INSERT", null, snapshot(dto), actor.userId());
        return entity;
    }

    public <E> E update(Resource r, Class<E> type, Long id, JsonNode body, Class<?> updateDto, CurrentUser actor) {
        Patch.Parsed<?> parsed = patch.parse(body, updateDto);
        Set<String> allowed = snapshot(parsed.dto()).keySet();
        E entity = type.cast(db.lock(r.type(), id, r.label()));
        Map<String, Object> before = snapshot(entity);
        ObjectNode apply = mapper.createObjectNode();
        body.fields().forEachRemaining(f -> {
            if (allowed.contains(f.getKey()) && (!f.getValue().isNull() || r.nullable().contains(f.getKey()))) {
                apply.set(f.getKey(), f.getValue());
            }
        });
        try {
            mapper.readerForUpdating(entity).readValue(apply);
        } catch (java.io.IOException e) {
            throw ApiException.unprocessable("Request validation failed", List.of(Map.of("field", "body", "message", "invalid value")));
        }
        Map<String, Object> after = snapshot(entity);
        Map<String, Object> oldV = new LinkedHashMap<>(), newV = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v) && apply.has(k)) {
                oldV.put(k, before.get(k));
                newV.put(k, v);
            }
        });
        if (newV.isEmpty()) return entity;
        if (r.hook() != null) r.hook().check(db, entity, false);
        dupCheck(r, entity, false);
        db.flush();
        audit.record(r.table(), id, "UPDATE", oldV, newV, actor.userId());
        return entity;
    }

    public void delete(Resource r, Long id, CurrentUser actor) {
        Object entity = db.lock(r.type(), id, r.label());
        audit.record(r.table(), id, "DELETE", snapshot(entity), null, actor.userId());
        db.remove(entity);
        db.flush();   // FK RESTRICT surfaces as 409 IN_USE through the global handler
    }

    // ---- resource-specific validation ------------------------------------------------------------------

    public static final Hook REGION_HOOK = (db, entity, isNew) -> {
        Region region = (Region) entity;
        Long parent = region.getParentRegionId();
        if (parent == null) return;
        db.get(Region.class, parent, "Parent region");
        if (!isNew) {
            Set<Long> seen = new HashSet<>();
            Long cur = parent;
            while (cur != null && seen.add(cur)) {
                if (cur.equals(region.getRegionId())) {
                    throw ApiException.conflict("REGION_CYCLE", "A region cannot be its own ancestor (parent chain would form a cycle)");
                }
                cur = db.first(Long.class, "select r.parentRegionId from Region r where r.regionId = :id", "id", cur);
            }
        }
    };

    public static final Hook MODEL_HOOK = (db, entity, isNew) -> {
        Long manufacturerId = (Long) prop(entity, "manufacturerId");
        if (db.find(VehicleManufacturer.class, manufacturerId) == null) {
            throw ApiException.badRequest("INVALID_REFERENCE", "Manufacturer does not exist");
        }
    };

    @Transactional(readOnly = true)
    public List<RegionNode> regionTree(Long rootId) {
        List<Region> rows = db.list(Region.class, "select r from Region r order by r.regionName");
        Map<Long, RegionNode> nodes = new LinkedHashMap<>();
        rows.forEach(r -> nodes.put(r.getRegionId(), new RegionNode(r.getRegionId(), r.getRegionName(), r.getRegionCode(), r.getParentRegionId())));
        List<RegionNode> roots = new ArrayList<>();
        for (Region r : rows) {
            RegionNode node = nodes.get(r.getRegionId());
            if (r.getParentRegionId() != null && nodes.containsKey(r.getParentRegionId())) nodes.get(r.getParentRegionId()).children().add(node);
            else roots.add(node);
        }
        if (rootId != null) {
            db.get(Region.class, rootId, "Region");
            return List.of(nodes.get(rootId));
        }
        return roots;
    }
}
