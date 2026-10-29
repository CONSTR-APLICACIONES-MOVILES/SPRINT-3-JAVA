package com.example.parchapp.data;

/** Combined activity access used by screens; concrete adapters can implement only their own role. */
public interface ActivityRepository extends ActivityPlanRepository, ActivityActionsRepository { }
