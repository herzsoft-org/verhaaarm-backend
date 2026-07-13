package moe.herz.verhaarmbackend.amt.dto;

import java.util.List;
import java.util.UUID;

public record SetAmtHoldersRequest(List<UUID> userIds) {}
