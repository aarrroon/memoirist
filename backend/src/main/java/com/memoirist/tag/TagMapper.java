package com.memoirist.tag;

import com.memoirist.tag.dto.TagResponse;

/** Tag to DTO, written by hand (no MapStruct, backend rules). The count comes from the service, not from the tag. */
final class TagMapper {

    private TagMapper() {}

    static TagResponse toResponse(Tag tag, long entryCount) {
        return new TagResponse(tag.getId(), tag.getName(), entryCount);
    }
}
