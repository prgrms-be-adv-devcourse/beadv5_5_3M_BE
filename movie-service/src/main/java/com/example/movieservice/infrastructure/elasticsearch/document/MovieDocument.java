package com.example.movieservice.infrastructure.elasticsearch.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;

@Document(indexName = "movies")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private Long movieId;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori"),
            otherFields = {
                    @InnerField(suffix = "autocomplete", type = FieldType.Search_As_You_Type, analyzer = "nori")
            }
    )
    private String title;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String description;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori"),
            otherFields = {
                    @InnerField(suffix = "autocomplete", type = FieldType.Search_As_You_Type, analyzer = "nori")
            }
    )
    private String creatorNickname;

    @Field(type = FieldType.Keyword)
    private List<Long> categoryIds;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private List<String> categoryNames;

    @Field(type = FieldType.Keyword)
    private String visibility;
}
