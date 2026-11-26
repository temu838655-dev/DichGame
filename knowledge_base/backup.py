"""Consistent SQLite backup and validated restore helpers."""
from __future__ import annotations

import os
import sqlite3
import tempfile
from contextlib import closing
from pathlib import Path

REQUIRED_TABLES = {
    "games", "translations", "data_sources", "glossary",
    "characters", "ocr_dictionary", "translation_cache", "source_strings",
}


def create_backup(database_path: str | Path, backup_path: str | Path) -> Path:
    source = Path(database_path).expanduser().resolve()
    destination = Path(backup_path).expanduser().resolve()
    if source == destination:
        raise ValueError("Backup destination must differ from the live database")
    if not source.is_file():
        raise FileNotFoundError(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(
        prefix=destination.name + ".", suffix=".tmp", dir=destination.parent,
    )
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        with closing(sqlite3.connect(str(source), timeout=15)) as live:
            with closing(sqlite3.connect(str(temporary))) as target:
                live.backup(target)
                result = target.execute("PRAGMA integrity_check").fetchone()[0]
                if result != "ok":
                    raise sqlite3.DatabaseError(f"Backup integrity check failed: {result}")
        os.replace(temporary, destination)
        return destination
    finally:
        temporary.unlink(missing_ok=True)


def validate_backup(backup_path: str | Path) -> None:
    path = Path(backup_path).expanduser().resolve()
    if not path.is_file():
        raise FileNotFoundError(path)
    with closing(sqlite3.connect(f"file:{path}?mode=ro", uri=True)) as db:
        result = db.execute("PRAGMA integrity_check").fetchone()[0]
        if result != "ok":
            raise sqlite3.DatabaseError(f"Backup integrity check failed: {result}")
        tables = {
            row[0] for row in db.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            )
        }
        missing = REQUIRED_TABLES - tables
        if missing:
            raise sqlite3.DatabaseError(
                "Backup is missing required tables: " + ", ".join(sorted(missing))
            )


def restore_backup(backup_path: str | Path, database_path: str | Path) -> Path:
    """Atomically replace a database after integrity/schema validation.

    Stop the application and close all database connections before restoring.
    """
    source = Path(backup_path).expanduser().resolve()
    destination = Path(database_path).expanduser().resolve()
    if source == destination:
        raise ValueError("Backup source and live database destination must differ")
    validate_backup(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(
        prefix=destination.name + ".", suffix=".restore", dir=destination.parent,
    )
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        with closing(sqlite3.connect(f"file:{source}?mode=ro", uri=True)) as backup_db:
            with closing(sqlite3.connect(str(temporary))) as restored:
                backup_db.backup(restored)
                if restored.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                    raise sqlite3.DatabaseError("Restored database failed integrity check")
        os.replace(temporary, destination)
        return destination
    finally:
        temporary.unlink(missing_ok=True)
