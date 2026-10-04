package com.afrochow.content.model;

import java.util.List;

public record PolicySection(String id, String heading, List<ContentBlock> blocks) {
}
