#!/usr/bin/env python3
"""Validate that a new Release keeps the versionCode delta expected by the in-app updater."""

from __future__ import annotations

import argparse


def expected_versions(
    previous_build: int,
    current_build: int,
    previous_phone: int,
    previous_tv: int,
) -> tuple[int, int]:
    values = (previous_build, current_build, previous_phone, previous_tv)
    if any(value <= 0 for value in values):
        raise ValueError("Các số build/versionCode phải là số nguyên dương.")
    if current_build <= previous_build:
        raise ValueError(
            f"Số build mới ({current_build}) phải lớn hơn build trước ({previous_build})."
        )
    delta = current_build - previous_build
    return previous_phone + delta, previous_tv + delta


def validate_release(
    previous_build: int,
    current_build: int,
    previous_phone: int,
    previous_tv: int,
    current_phone: int,
    current_tv: int,
) -> tuple[int, int]:
    expected_phone, expected_tv = expected_versions(
        previous_build, current_build, previous_phone, previous_tv
    )
    if current_phone != expected_phone:
        raise ValueError(
            f"versionCode điện thoại sai: cần {expected_phone}, nhận {current_phone}."
        )
    if current_tv != expected_tv:
        raise ValueError(
            f"versionCode Android TV sai: cần {expected_tv}, nhận {current_tv}."
        )
    return expected_phone, expected_tv


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--previous-build", type=int, required=True)
    parser.add_argument("--current-build", type=int, required=True)
    parser.add_argument("--previous-phone", type=int, required=True)
    parser.add_argument("--previous-tv", type=int, required=True)
    parser.add_argument("--current-phone", type=int, required=True)
    parser.add_argument("--current-tv", type=int, required=True)
    args = parser.parse_args()
    try:
        phone, tv = validate_release(
            args.previous_build,
            args.current_build,
            args.previous_phone,
            args.previous_tv,
            args.current_phone,
            args.current_tv,
        )
    except ValueError as exc:
        parser.error(str(exc))
    print(f"versionCode điện thoại hợp lệ: {phone}")
    print(f"versionCode Android TV hợp lệ: {tv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
