-- ============================================================
-- RTO Management System
-- 01_create_database.sql
-- Creates the schema container and sets session defaults.
-- Run this first.
-- ============================================================

CREATE DATABASE IF NOT EXISTS rto_management
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rto_management;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;
