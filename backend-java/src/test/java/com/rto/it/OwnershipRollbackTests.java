package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.Audit;
import com.rto.domain.Citizen;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Map;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/** Proves ownership transfer approval is atomic: a crash AFTER the ownership rows changed leaves nothing behind. */
class OwnershipRollbackTests extends BaseIT {
    @SpyBean Audit audit;

    @Test
    void aFailureMidApprovalRollsEverythingBack() {
        Env env = flows.makeEnv();
        Fx.Refs refs = fx.makeVehicleRefs(false);
        Citizen seller = env.citizen, buyer = fx.makeCitizen();
        Map<String, Object> body = m("registration_number", "MH12" + Fx.u(6), "chassis_number", "CH" + Fx.u(12), "engine_number", "EN" + Fx.u(12),
                "manufacture_year", 2020, "registering_office_id", env.office.getOfficeId(), "owner_citizen_id", seller.getCitizenId(), "registration_date", "2020-01-15");
        body.putAll(refs.body());
        long vid = api.post("/api/v1/vehicles", env.token, body).l("vehicle_id");
        JsonNode app = flows.newApp(env.with(buyer));
        long tid = api.post("/api/v1/ownership-transfers", env.token, m("vehicle_id", vid, "to_citizen_id", buyer.getCitizenId(),
                "application_id", app.get("application_id").asLong())).l("transfer_id");

        // Raised after the old row was closed, the new row opened and the transfer marked APPROVED (see OwnershipService.approve).
        doThrow(new RuntimeException("simulated crash")).when(audit).record(eq("vehicle_ownerships"), any(), any(), any(), any(), any());
        try {
            Resp r = api.post("/api/v1/ownership-transfers/" + tid + "/approve", env.token);
            assertThat(r.status()).isEqualTo(500);
        } finally {
            reset(audit);
            doCallRealMethod().when(audit).record(any(), any(), any(), any(), any(), any());
        }

        List<Map<String, Object>> rows = fx.jdbc.queryForList("SELECT citizen_id, effective_to FROM vehicle_ownerships WHERE vehicle_id = ?", vid);
        assertThat(rows).hasSize(1);   // nothing partially applied: the old row is not closed, no new row exists
        assertThat(((Number) rows.get(0).get("citizen_id")).longValue()).isEqualTo(seller.getCitizenId());
        assertThat(rows.get(0).get("effective_to")).isNull();
        assertThat(fx.jdbc.queryForObject("SELECT status FROM ownership_transfers WHERE transfer_id = ?", String.class, tid)).isEqualTo("PENDING");
        assertThat(fx.jdbc.queryForObject("SELECT approved_at IS NULL FROM ownership_transfers WHERE transfer_id = ?", Boolean.class, tid)).isTrue();
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'ownership_transfers' AND record_id = ? AND action = 'UPDATE'", tid)).isEqualTo(0);

        assertThat(api.post("/api/v1/ownership-transfers/" + tid + "/approve", env.token).status()).isEqualTo(200);   // and it still works afterwards
        assertThat(fx.count("SELECT COUNT(*) FROM vehicle_ownerships WHERE vehicle_id = ? AND effective_to IS NULL", vid)).isEqualTo(1);
    }
}
