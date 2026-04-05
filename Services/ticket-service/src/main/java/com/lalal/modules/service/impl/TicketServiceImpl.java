package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.dto.SeatSelectionRequestDTO;
import com.lalal.modules.dto.TicketDTO;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketDO;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.mapper.TicketMapper;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.remote.OrderServiceClient;
import com.lalal.modules.remote.SeatServiceClient;
import com.lalal.modules.remote.UserServiceClient;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService {

    private final SeatServiceClient seatServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final FareCalculationService fareCalculationService;
    private final TrainMapper trainMapper;

    private static final String PEAK_STATUS_KEY = "traffic:peak:status";

    @Override
    public PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto, Long userId) {
        PurchaseTicketVO purchaseTicketVO = new PurchaseTicketVO();

        if (userId == null) {
            purchaseTicketVO.setStatus(RequestStatus.FAILED.toString());
            return purchaseTicketVO;
        }
        if (purchaseTicketRequestDto.getIDCardCodelist() == null
                || purchaseTicketRequestDto.getSeatTypelist() == null
                || purchaseTicketRequestDto.getIDCardCodelist().size() != purchaseTicketRequestDto.getSeatTypelist().size()) {
            purchaseTicketVO.setStatus(RequestStatus.FAILED.toString());
            return purchaseTicketVO;
        }

        UserServiceClient.PassengersBatchRequest batchReq = new UserServiceClient.PassengersBatchRequest();
        batchReq.setUserId(userId);
        batchReq.setPassengerIds(purchaseTicketRequestDto.getIDCardCodelist());
        Result<List<UserServiceClient.PassengerRemoteVO>> passengerResult = userServiceClient.batchPassengers(batchReq);
        if (passengerResult == null
                || passengerResult.getCode() == null
                || !ReturnCode.success.code().equals(passengerResult.getCode())
                || passengerResult.getData() == null
                || passengerResult.getData().isEmpty()) {
            purchaseTicketVO.setStatus(RequestStatus.FAILED.toString());
            return purchaseTicketVO;
        }
        List<UserServiceClient.PassengerRemoteVO> passengers = passengerResult.getData();

        String peakStatus = redisTemplate.opsForValue().get(PEAK_STATUS_KEY);
        boolean peakHour = "true".equals(peakStatus);

        if (peakHour) {
            purchaseTicketVO.setStatus(RequestStatus.PROCESSING.toString());
            return purchaseTicketVO;
        }

        SeatSelectionRequestDTO seatRequest = new SeatSelectionRequestDTO();
        seatRequest.setTrainNum(purchaseTicketRequestDto.getTrainNum());
        seatRequest.setStartStation(purchaseTicketRequestDto.getStartStation());
        seatRequest.setEndStation(purchaseTicketRequestDto.getEndStation());
        seatRequest.setDate(java.time.LocalDate.parse(purchaseTicketRequestDto.getDate()));
        String account = StringUtils.hasText(purchaseTicketRequestDto.getAccount())
                ? purchaseTicketRequestDto.getAccount()
                : "";
        seatRequest.setAccount(account);

        List<SeatSelectionRequestDTO.PassengerDTO> seatPassengers = new ArrayList<>();
        for (int i = 0; i < passengers.size(); i++) {
            SeatSelectionRequestDTO.PassengerDTO p = new SeatSelectionRequestDTO.PassengerDTO();
            p.setId(passengers.get(i).getId());
            p.setSeatType(purchaseTicketRequestDto.getSeatTypelist().get(i));
            if (purchaseTicketRequestDto.getChooseSeats() != null && i < purchaseTicketRequestDto.getChooseSeats().size()) {
                p.setSeatPreference(purchaseTicketRequestDto.getChooseSeats().get(i));
            }
            seatPassengers.add(p);
        }
        seatRequest.setPassengers(seatPassengers);

        TicketDTO selectedSeats = seatServiceClient.select(seatRequest);
        if (selectedSeats == null) {
            purchaseTicketVO.setStatus(RequestStatus.FAILED.toString());
            return purchaseTicketVO;
        }

        // 获取列车信息
        TrainDO trainDO = trainMapper.selectById(
                trainMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrainDO>()
                                .eq(TrainDO::getTrainNumber, purchaseTicketRequestDto.getTrainNum())
                ).stream().findFirst().map(TrainDO::getId).orElse(null)
        );

        // 构建票价计算请求
        List<FareCalculationRequestDTO> fareRequests = new ArrayList<>();
        for (int i = 0; i < selectedSeats.getItems().size(); i++) {
            TicketDTO.TicketItem item = selectedSeats.getItems().get(i);
            FareCalculationRequestDTO fareRequest = new FareCalculationRequestDTO();
            fareRequest.setTrainNumber(purchaseTicketRequestDto.getTrainNum());
            fareRequest.setDepartureStation(purchaseTicketRequestDto.getStartStation());
            fareRequest.setArrivalStation(purchaseTicketRequestDto.getEndStation());
            fareRequest.setSeatType(item.getSeatType());
            fareRequest.setPassengerType(0); // 默认成人
            fareRequest.setTrainBrand(trainDO != null ? trainDO.getTrainBrand() : null);
            fareRequest.setIsPeakSeason(false);
            fareRequest.setPassengerId(item.getPassengerId());
            if (trainDO != null) {
                fareRequest.setTrainId(trainDO.getId());
            }
            fareRequests.add(fareRequest);
        }

        // 计算票价
        List<FareCalculationResultDTO> fareResults = fareCalculationService.batchCalculateFare(fareRequests);

        OrderServiceClient.OrderCreateRemoteRequestDTO orderRequest = new OrderServiceClient.OrderCreateRemoteRequestDTO();
        orderRequest.setTrainNumber(purchaseTicketRequestDto.getTrainNum());
        orderRequest.setStartStation(purchaseTicketRequestDto.getStartStation());
        orderRequest.setEndStation(purchaseTicketRequestDto.getEndStation());
        orderRequest.setUsername(account);
        orderRequest.setRunDate(new Date());

        List<OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO> orderItems = new ArrayList<>();
        for (int i = 0; i < selectedSeats.getItems().size(); i++) {
            TicketDTO.TicketItem item = selectedSeats.getItems().get(i);
            FareCalculationResultDTO fareResult = fareResults.get(i);

            OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO orderItem =
                    new OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO();
            orderItem.setPassengerId(item.getPassengerId());
            orderItem.setCarriageNumber(item.getCarriageNum());
            orderItem.setSeatNumber(item.getSeatNum());
            orderItem.setSeatType(item.getSeatType());
            orderItem.setAmount(fareResult.getTotalFare());

            UserServiceClient.PassengerRemoteVO matched = passengers.stream()
                    .filter(pv -> pv.getId().equals(item.getPassengerId()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                orderItem.setIdCard(matched.getIdCardNumber());
                orderItem.setRealName(matched.getRealName());
            } else {
                orderItem.setIdCard("");
                orderItem.setRealName("");
            }
            orderItems.add(orderItem);
        }
        orderRequest.setItems(orderItems);

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        purchaseTicketVO.setTotalAmount(totalAmount);

        String orderSn = orderServiceClient.create(orderRequest);
        purchaseTicketVO.setOrderSn(orderSn);
        purchaseTicketVO.setStatus(RequestStatus.SUCCESS.toString());
        purchaseTicketVO.setTicketDTO(selectedSeats);
        return purchaseTicketVO;
    }

    @Override
    public PurchaseTicketVO check(String RequestId) {
        return null;
    }
}
