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

    // 검색 결과 카드에 포스터 표시용. 검색 대상 X (index = false).
    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    // 검색 결과 카드에 별점 표시용. 검색 대상 X (FE 카드 정렬은 _score 기준).
    @Field(type = FieldType.Float, index = false)
    private Float averageRating;

    // 검색 결과 카드의 creator 페이지 이동용. 검색 대상 X.
    @Field(type = FieldType.Keyword, index = false)
    private String creatorId;

    // 검색 결과 카드의 좋아요 수 표시용. 검색 대상 X.
    @Field(type = FieldType.Integer, index = false)
    private Integer likeCount;

    // 검색 결과 카드의 리뷰 수 표시용. 검색 대상 X.
    @Field(type = FieldType.Integer, index = false)
    private Integer reviewCount;
}
