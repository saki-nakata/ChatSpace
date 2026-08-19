package com.chatspace.api.notification;

import com.chatspace.api.common.Cursor;
import java.util.List;

public record NotificationListResponse(
    List<NotificationResponse> notifications, Cursor nextCursor) {}
