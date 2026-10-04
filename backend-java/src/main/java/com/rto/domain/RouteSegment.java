package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `route_segments`). */
@Entity
@Table(name = "route_segments")
@Getter
@Setter
@NoArgsConstructor
public class RouteSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "segment_name", nullable = false)
    private String segmentName;

}
