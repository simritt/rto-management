package com.rto.core;

import java.util.Map;

/** MySQL constraint/key name -> business message (keys come straight from database/*.sql). Generated from the reference backend. */
final class DbMessages {
    private DbMessages() {}

    static final Map<String, String> UNIQUE = Map.ofEntries(
            Map.entry("uq_person_national_id", "A person with this national ID already exists"),
            Map.entry("uq_citizen_code", "Citizen code already exists"),
            Map.entry("uq_citizen_person", "This person is already registered as a citizen"),
            Map.entry("uq_employee_code", "Employee code already exists"),
            Map.entry("uq_employee_person", "This person is already an employee"),
            Map.entry("uq_employee_current_posting", "Employee already has a current posting; close it first"),
            Map.entry("uq_office_code", "Office code already exists"),
            Map.entry("uq_office_counter", "Counter number already exists in this office"),
            Map.entry("uq_username", "Username already exists"),
            Map.entry("uq_role_name", "Role name already exists"),
            Map.entry("uq_permission_key", "Permission key already exists"),
            Map.entry("uq_region_code", "Region code already exists"),
            Map.entry("uq_manufacturer_name", "Manufacturer already exists"),
            Map.entry("uq_manufacturer_model", "This model already exists for the manufacturer"),
            Map.entry("uq_vehicle_type_name", "Vehicle type already exists"),
            Map.entry("uq_fuel_name", "Fuel type already exists"),
            Map.entry("uq_class_code", "Licence class code already exists"),
            Map.entry("uq_doc_type_name", "Document type already exists"),
            Map.entry("uq_service_code", "Service code already exists"),
            Map.entry("uq_violation_desc", "Violation type already exists"),
            Map.entry("uq_permit_type_name", "Permit type already exists"),
            Map.entry("uq_payable_type_name", "Payable type already exists"),
            Map.entry("uq_department_name", "Department already exists"),
            Map.entry("uq_designation_title", "Designation already exists"),
            Map.entry("uq_application_number", "Application number already exists"),
            Map.entry("uq_applicant_citizen", "Applicant already exists for this citizen"),
            Map.entry("uq_appointment_application", "This application already has an appointment record"),
            Map.entry("uq_slot", "An appointment slot already exists for this office/counter/date/start time"),
            Map.entry("uq_school_licence", "Driving school licence number already exists"),
            Map.entry("uq_instructor_person", "This person is already a driving instructor"),
            Map.entry("uq_learner_licence_number", "Learner licence number already exists"),
            Map.entry("uq_citizen_active_learner", "Citizen already has an ACTIVE learner licence"),
            Map.entry("uq_driving_licence_number", "Driving licence number already exists"),
            Map.entry("uq_vehicle_registration", "Vehicle registration number already exists"),
            Map.entry("uq_vehicle_chassis", "Chassis number already exists"),
            Map.entry("uq_vehicle_engine", "Engine number already exists"),
            Map.entry("uq_vehicle_current_owner", "Vehicle already has another current owner"),
            Map.entry("uq_policy_number", "Insurance policy number already exists"),
            Map.entry("uq_route_sequence", "A segment with this sequence number already exists on the route"),
            Map.entry("uq_permit_number", "Permit number already exists"),
            Map.entry("uq_challan_number", "Challan number already exists"),
            Map.entry("uq_receipt_number", "Receipt number already exists"),
            Map.entry("uq_payment_attempt", "Payment attempt number already recorded"));

    static final Map<String, String> CHECK = Map.ofEntries(
            Map.entry("chk_slot_capacity", "Appointment slot capacity exceeded"),
            Map.entry("chk_learner_dates", "Expiry date must be after issue date"),
            Map.entry("chk_dl_dates", "Expiry date must be after issue date"),
            Map.entry("chk_fitness_dates", "Expiry date must be after issue date"),
            Map.entry("chk_puc_dates", "Expiry date must be after issue date"),
            Map.entry("chk_insurance_dates", "End date must be after start date"),
            Map.entry("chk_permit_dates", "Expiry date must be after issue date"));
}
