/*
Copyright 2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

/**
 * Installs a window.fetch interceptor to extract `fstick_events` from Matrix
 * /sync responses and dispatch them as DOM CustomEvents (`fstick:sync-event`).
 *
 * Import this module once at startup — it is idempotent.
 */

export const FSTICK_SYNC_EVENT = "fstick:sync-event";

export interface FstickSyncEventDetail {
    type: string;
    content: Record<string, unknown>;
}

let installed = false;

function install(): void {
    if (installed) return;
    installed = true;

    const originalFetch = window.fetch.bind(window);

    window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        const response = await originalFetch(input, init);

        const url =
            typeof input === "string"
                ? input
                : input instanceof URL
                  ? input.href
                  : (input as Request).url;

        // Only intercept Matrix /sync long-poll responses
        if (url.includes("/_matrix/client/") && url.includes("/sync")) {
            response
                .clone()
                .json()
                .then((data: unknown) => {
                    const events = (data as Record<string, unknown>)?.fstick_events;
                    if (Array.isArray(events)) {
                        for (const ev of events as FstickSyncEventDetail[]) {
                            window.dispatchEvent(
                                new CustomEvent<FstickSyncEventDetail>(FSTICK_SYNC_EVENT, {
                                    detail: ev,
                                }),
                            );
                        }
                    }
                })
                .catch(() => {
                    // Non-JSON or error responses — ignore
                });
        }

        return response;
    };
}

install();

