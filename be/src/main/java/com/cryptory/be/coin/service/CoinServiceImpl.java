package com.cryptory.be.coin.service;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cryptory.be.chart.domain.Chart;
import com.cryptory.be.chart.dto.ChartDto;
import com.cryptory.be.chart.repository.ChartRepository;
import com.cryptory.be.coin.domain.Coin;
import com.cryptory.be.coin.dto.CoinDetailDto;
import com.cryptory.be.coin.dto.CoinNewsDto;
import com.cryptory.be.global.util.DateFormat;
import com.cryptory.be.openapi.dto.NaverNews;
import com.cryptory.be.openapi.dto.Ticker;
import com.cryptory.be.openapi.service.NaverService;
import com.cryptory.be.openapi.service.UpbitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;


import com.cryptory.be.coin.dto.CoinDto;
import com.cryptory.be.coin.repository.CoinRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinServiceImpl implements CoinService {

    private final NaverService naverService;
    private final UpbitService upbitService;

    private final CoinRepository coinRepository;
    private final ChartRepository chartRepository;


    // 코인 목록 조회
    @Override
    public List<CoinDto> getCoins() {
        // 화면에 보여지는 코인만 조회
        List<Coin> coins = coinRepository.findAll().stream()
                .filter(Coin::isDisplayed)
                .toList();

        // 코인 코드 목록
        String[] codes = coins.stream()
                .map(Coin::getCode)
                .toArray(String[]::new);

        // 코인 목록에선 현재가, 변화액, 변화율 필요
        List<Ticker> tickers = upbitService.getTickers(codes);

        // Dto 담기 위해 Map으로 변환
        Map<String, Ticker> tickerMap = tickers.stream()
                .collect(Collectors.toMap(Ticker::getMarket, ticker -> ticker));

        log.info("tickerMap: {}", tickerMap);

        return coins.stream()
                .map(coin ->
                    CoinDto.builder()
                            .coinId(coin.getId())
                            .koreanName(coin.getKoreanName())
                            .englishName(coin.getEnglishName())
                            .code(coin.getCode())
                            .coinSymbol(coin.getCoinSymbol())
                            .tradePrice(tickerMap.get(coin.getCode()).getTradePrice())
                            .signedChangePrice(tickerMap.get(coin.getCode()).getSignedChangePrice())
                            .signedChangeRate(tickerMap.get(coin.getCode()).getSignedChangeRate())
                            .build())
                .toList();
    }

    // 특정 코인 상세 조회
    @Override
    public CoinDetailDto getCoinDetail(Long coinId) {
        Coin coin = coinRepository.findById(coinId)
                .orElseThrow(() -> new IllegalArgumentException("해당 코인이 없습니다."));

        // 코인의 차트 데이터 조회
        List<ChartDto> charts = chartRepository.findAllByCoinId(coin.getId()).stream()
                .map(chart -> ChartDto.builder()
                        .chartId(chart.getId())
                        .date(chart.getDate())
                        .openingPrice(chart.getOpeningPrice())
                        .tradePrice(chart.getTradePrice())
                        .highPrice(chart.getHighPrice())
                        .lowPrice(chart.getLowPrice())
                        .changeRate(chart.getChangeRate())
                        .build())
                .toList();

        // 코인 하나의 현재가(Ticker) 반환
        Ticker coinTicker = upbitService.getTickers(coin.getCode()).get(0);

        return CoinDetailDto.builder()
                .coinId(coin.getId())
                .koreanName(coin.getKoreanName())
                .englishName(coin.getEnglishName())
                .code(coin.getCode())
                .coinSymbol(coin.getCoinSymbol())
                .tradePrice(coinTicker.getTradePrice())
                .signedChangeRate(coinTicker.getSignedChangeRate())
                .signedChangePrice(coinTicker.getSignedChangePrice())
                .timestamp(DateFormat.formatTradeTime(coinTicker.getTradeDate(), coinTicker.getTradeTime()))
                .chartList(charts)
                .build();

        // TODO 이슈 목록 조회(코인에 연관관계 매핑해서 조회할지, 차트 ID로 조회할지)
    }

    // 특정 코인 뉴스 조회
    @Override
    public List<CoinNewsDto> getCoinNews(Long coinId) {
        Coin coin = coinRepository.findById(coinId)
                .orElseThrow(() -> new IllegalArgumentException("해당 코인이 없습니다."));

        List<NaverNews> naverNewsList = naverService.getNaverNewsWithWord(coin.getKoreanName());

        return naverNewsList.stream()
                .map(naverNews -> {
                    try {
                        return new CoinNewsDto(naverNews.getTitle(), naverNews.getLink(),
                                naverNews.getDescription(), DateFormat.formatNewsDate(naverNews.getPubDate()));
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

}
